package com.sentinelai.graph;

import com.sentinelai.alert.domain.Alert;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.graph.GraphDtos.Edge;
import com.sentinelai.graph.GraphDtos.GraphResponse;
import com.sentinelai.graph.GraphDtos.KillChainStage;
import com.sentinelai.graph.GraphDtos.Node;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.repository.IncidentAlertRepository;
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the attack-storyline graph (nodes, typed edges, kill-chain) for an incident. */
@Service
@RequiredArgsConstructor
public class GraphService {

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final IncidentAlertRepository incidentAlertRepository;

    @Value("${sentinel.graph.max-nodes:100}")
    private int maxNodes;

    @Transactional(readOnly = true)
    public GraphResponse build(Long incidentId, AppUserPrincipal actor) {
        Incident incident = incidentRepository.findById(incidentId)
                .filter(i -> i.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Incident not found: " + incidentId));

        Map<String, Node> nodes = new LinkedHashMap<>();
        Map<String, Edge> edges = new LinkedHashMap<>();
        String incidentNode = "incident:" + incidentId;
        nodes.put(incidentNode, new Node(incidentNode, "incident", "Incident #" + incidentId));

        for (var link : incidentAlertRepository.findById_IncidentId(incidentId)) {
            Alert a = link.getAlert();
            String an = "alert:" + a.getId();
            nodes.put(an, new Node(an, "alert", a.getRuleType()));
            edge(edges, incidentNode, an, "HAS_ALERT", a.getCreatedAt());
        }

        boolean[] truncated = {false};
        for (var link : incidentEventRepository.findById_IncidentId(incidentId)) {
            SecurityEvent e = link.getEvent();
            Instant ts = e.getEventTimestamp();
            String user = e.getUsername() != null ? "user:" + e.getUsername() : null;
            String ip = e.getSourceIp() != null ? "ip:" + e.getSourceIp() : null;
            String resource = e.getResource() != null ? "resource:" + e.getResource() : null;

            // Only draw an edge when both endpoints survived the node cap.
            if (addNode(nodes, user, "user", e.getUsername(), truncated)) {
                edge(edges, incidentNode, user, "INVOLVES", ts);
            }
            if (addNode(nodes, ip, "ip", e.getSourceIp(), truncated) && nodes.containsKey(user)) {
                edge(edges, user, ip, "LOGIN_FROM", ts);
            }
            if (addNode(nodes, resource, "resource", e.getResource(), truncated) && nodes.containsKey(ip)) {
                edge(edges, ip, resource, "ACCESSED", ts);
            }
            if (e.isHoneytoken() && nodes.containsKey(user)) {
                String ht = "honeytoken:" + e.getId();
                if (addNode(nodes, ht, "honeytoken", "honeytoken", truncated)) {
                    edge(edges, user, ht, "TOUCHED", ts);
                }
            }
        }
        if (truncated[0]) {
            nodes.put("aggregate", new Node("aggregate", "aggregate", "… more (capped at " + maxNodes + ")"));
        }

        return new GraphResponse(incidentId, new ArrayList<>(nodes.values()),
                new ArrayList<>(edges.values()), killChain(incidentId), truncated[0]);
    }

    /** Adds a node if there's room; returns true if the node is present afterwards. */
    private boolean addNode(Map<String, Node> nodes, String id, String type, String label, boolean[] truncated) {
        if (id == null) {
            return false;
        }
        if (nodes.containsKey(id)) {
            return true;
        }
        if (nodes.size() >= maxNodes) {
            truncated[0] = true;
            return false;
        }
        nodes.put(id, new Node(id, type, label));
        return true;
    }

    private void edge(Map<String, Edge> edges, String from, String to, String type, Instant ts) {
        String key = from + "|" + to + "|" + type;
        Edge existing = edges.get(key);
        if (existing == null) {
            edges.put(key, new Edge(from, to, type, 1, ts));
        } else {
            Instant first = existing.firstSeen().isBefore(ts) ? existing.firstSeen() : ts;
            edges.put(key, new Edge(from, to, type, existing.count() + 1, first));
        }
    }

    private List<KillChainStage> killChain(Long incidentId) {
        Map<Integer, KillChainStage> byStage = new LinkedHashMap<>();
        for (var link : incidentAlertRepository.findById_IncidentId(incidentId)) {
            Alert a = link.getAlert();
            KillChain.Stage s = KillChain.forTechnique(a.getMitreTechnique());
            byStage.merge(s.index(),
                    new KillChainStage(s.index(), s.tactic(), a.getMitreTechnique(), 1, a.getCreatedAt()),
                    (x, y) -> new KillChainStage(x.stage(), x.tactic(), x.technique(), x.count() + 1,
                            x.firstSeen().isBefore(y.firstSeen()) ? x.firstSeen() : y.firstSeen()));
        }
        return byStage.values().stream().sorted(Comparator.comparingInt(KillChainStage::stage)).toList();
    }
}
