package com.sentinelai.incident.similarity;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.cache.IncidentsChangedEvent;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.incident.domain.IncidentFeedback;
import com.sentinelai.incident.repository.IncidentAlertRepository;
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.playbook.domain.PlaybookActionStatus;
import com.sentinelai.playbook.repository.PlaybookActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature-vector cosine similarity over an org's incidents. Each incident becomes a sparse vector of
 * rule types, MITRE techniques, entity type, severity, time-of-day bucket and event-type counts.
 * Per-org vectors are cached and invalidated whenever incidents change.
 */
@Service
@RequiredArgsConstructor
public class CosineSimilarityService implements SimilarityService {

    private final IncidentRepository incidentRepository;
    private final IncidentAlertRepository incidentAlertRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final PlaybookActionRepository playbookActionRepository;

    /** orgId -> (incidentId -> feature vector). */
    private final Map<Long, Map<Long, Map<String, Double>>> cache = new ConcurrentHashMap<>();

    @EventListener
    public void onIncidentsChanged(IncidentsChangedEvent event) {
        cache.remove(event.orgId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimilarIncident> findSimilar(Long incidentId, AppUserPrincipal actor, int limit) {
        Incident target = incidentRepository.findById(incidentId)
                .filter(i -> i.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Incident not found: " + incidentId));
        Long orgId = actor.getOrgId();

        Map<Long, Map<String, Double>> vectors = vectors(orgId);
        Map<String, Double> targetVec = vectors.computeIfAbsent(incidentId, id -> vector(target));

        List<SimilarIncident> matches = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Double>> e : vectors.entrySet()) {
            if (e.getKey().equals(incidentId)) {
                continue;
            }
            double score = cosine(targetVec, e.getValue());
            if (score <= 0) {
                continue;
            }
            incidentRepository.findById(e.getKey()).ifPresent(other ->
                    matches.add(describe(other, score, shared(targetVec, e.getValue()))));
        }
        matches.sort(Comparator.comparingDouble(SimilarIncident::score).reversed());
        return matches.subList(0, Math.min(limit, matches.size()));
    }

    private SimilarIncident describe(Incident inc, double score, List<String> shared) {
        List<String> actions = playbookActionRepository.findByIncident_Id(inc.getId()).stream()
                .filter(a -> a.getStatus() == PlaybookActionStatus.EXECUTED)
                .map(a -> a.getActionType() + (a.getTargetRef() == null ? "" : " " + a.getTargetRef()))
                .toList();
        boolean resolvedWell = inc.getFeedback() == IncidentFeedback.TRUE_POSITIVE
                || inc.getStatus() == IncidentStatus.RESOLVED || inc.getStatus() == IncidentStatus.CONTAINED;
        return new SimilarIncident(inc.getId(), inc.getTitle(),
                inc.getSeverity() == null ? null : inc.getSeverity().name(),
                inc.getStatus() == null ? null : inc.getStatus().name(),
                inc.getFeedback() == null ? null : inc.getFeedback().name(),
                round(score), shared, actions, !actions.isEmpty() && resolvedWell);
    }

    private Map<Long, Map<String, Double>> vectors(Long orgId) {
        return cache.computeIfAbsent(orgId, id -> {
            Map<Long, Map<String, Double>> map = new ConcurrentHashMap<>();
            for (Incident inc : incidentRepository.findByOrg_Id(orgId)) {
                map.put(inc.getId(), vector(inc));
            }
            return map;
        });
    }

    private Map<String, Double> vector(Incident inc) {
        Map<String, Double> v = new LinkedHashMap<>();
        incidentAlertRepository.findById_IncidentId(inc.getId()).forEach(l -> {
            var alert = l.getAlert();
            bump(v, "rule:" + alert.getRuleType());
            if (alert.getMitreTechnique() != null) {
                v.put("mitre:" + alert.getMitreTechnique(), 1.0);
            }
        });
        incidentEventRepository.findById_IncidentId(inc.getId()).forEach(l ->
                bump(v, "event:" + l.getEvent().getEventType()));
        if (inc.getCorrelationKey() != null && inc.getCorrelationKey().contains(":")) {
            v.put("entity:" + inc.getCorrelationKey().split(":", 2)[0], 1.0);
        }
        if (inc.getSeverity() != null) {
            v.put("severity:" + inc.getSeverity().name(), 1.0);
        }
        if (inc.getCreatedAt() != null) {
            int bucket = inc.getCreatedAt().atOffset(ZoneOffset.UTC).getHour() / 6; // 0..3
            v.put("hour:" + bucket, 1.0);
        }
        return v;
    }

    private List<String> shared(Map<String, Double> a, Map<String, Double> b) {
        Set<String> keys = new TreeSet<>(a.keySet());
        keys.retainAll(b.keySet());
        return new ArrayList<>(keys);
    }

    private static void bump(Map<String, Double> v, String key) {
        v.merge(key, 1.0, Double::sum);
    }

    private static double cosine(Map<String, Double> a, Map<String, Double> b) {
        double dot = 0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            Double bv = b.get(e.getKey());
            if (bv != null) {
                dot += e.getValue() * bv;
            }
        }
        double na = norm(a);
        double nb = norm(b);
        return (na == 0 || nb == 0) ? 0 : dot / (na * nb);
    }

    private static double norm(Map<String, Double> v) {
        double s = 0;
        for (double d : v.values()) {
            s += d * d;
        }
        return Math.sqrt(s);
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
