package com.sentinelai.offense;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.ai.domain.AiAnalysis;
import com.sentinelai.ai.repository.AiAnalysisRepository;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.incident.domain.IncidentTimeline;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.incident.repository.IncidentTimelineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * One case timeline for an incident, merging the system timeline (detection, status, assignment,
 * priority, response actions), analyst notes and AI analyses into plain-language entries sorted by time.
 */
@Service
@RequiredArgsConstructor
public class CaseTimelineService {

    /** {@code kind}: DETECTION, STATUS, ASSIGNMENT, NOTE, ACTION, AI or SYSTEM (for UI filtering). */
    public record Entry(Instant at, String kind, String actor, String title, String detail) {
    }

    private static final Map<String, String> ACTION_VERBS = Map.of(
            "ACTION_PROPOSED", "proposed", "ACTION_APPROVED", "approved", "ACTION_EXECUTED", "executed",
            "ACTION_ROLLED_BACK", "rolled back", "ACTION_REJECTED", "rejected", "ACTION_FAILED", "failed");

    private final IncidentRepository incidentRepository;
    private final IncidentTimelineRepository timelineRepository;
    private final IncidentNoteRepository noteRepository;
    private final AiAnalysisRepository analysisRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<Entry> timeline(Long incidentId, AppUserPrincipal actor) {
        incidentRepository.findById(incidentId).filter(i -> i.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Incident not found: " + incidentId));
        List<Entry> out = new ArrayList<>();
        for (IncidentTimeline t : timelineRepository.findByIncidentIdOrderByIdAsc(incidentId)) {
            Entry e = fromTimeline(t);
            if (e != null) {
                out.add(e);
            }
        }
        for (IncidentNote n : noteRepository.findByIncident_IdOrderByIdAsc(incidentId)) {
            String who = n.getAuthor() == null ? "deleted user" : n.getAuthor().getUsername();
            out.add(new Entry(n.getCreatedAt(), "NOTE", who,
                    "Note by " + who + (n.getUpdatedAt() != null ? " (edited)" : ""), n.getBody()));
        }
        for (AiAnalysis a : analysisRepository.findByIncident_IdOrderByIdDesc(incidentId)) {
            out.add(new Entry(a.getCreatedAt(), "AI", a.getModelName() == null ? "ai" : a.getModelName(),
                    "AI analysis (" + a.getValidationStatus() + ", review " + a.getStatus() + ")", summary(a)));
        }
        out.sort(Comparator.comparing(Entry::at, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    private Entry fromTimeline(IncidentTimeline t) {
        JsonNode d = json(t.getDetail());
        String actor = t.getActor() == null ? "system" : t.getActor();
        String type = t.getType();
        if (ACTION_VERBS.containsKey(type)) {
            String action = d.path("action").asText("action").replace('_', ' ');
            String target = d.path("target").asText("");
            return new Entry(t.getCreatedAt(), "ACTION", actor,
                    "Response " + ACTION_VERBS.get(type) + ": " + action + (target.isEmpty() ? "" : " " + target),
                    d.path("result").asText(null));
        }
        return switch (type) {
            case "INCIDENT_CREATED" -> new Entry(t.getCreatedAt(), "DETECTION", actor, "Case opened",
                    "Correlated on " + d.path("correlationKey").asText("?"));
            case "ALERT_JOINED" -> new Entry(t.getCreatedAt(), "DETECTION", actor,
                    "Alert joined: " + d.path("ruleType").asText("?"), null);
            case "ESCALATED" -> new Entry(t.getCreatedAt(), "DETECTION", actor,
                    "Escalated to " + d.path("severity").asText("?"), null);
            case "RESCORED" -> new Entry(t.getCreatedAt(), "SYSTEM", actor,
                    "Risk rescored to " + d.path("score").asText("?") + " (" + d.path("severity").asText("?") + ")", null);
            case "STATUS_CHANGE" -> new Entry(t.getCreatedAt(), "STATUS", actor,
                    "Status " + label(d.path("from").asText()) + " → " + label(d.path("to").asText()), null);
            case "PRIORITY_CHANGE" -> new Entry(t.getCreatedAt(), "STATUS", actor,
                    "Priority " + d.path("from").asText() + " → " + d.path("to").asText(), null);
            case "FEEDBACK" -> new Entry(t.getCreatedAt(), "STATUS", actor,
                    "Marked " + d.path("feedback").asText().replace('_', ' ').toLowerCase(), null);
            case "ASSIGNMENT" -> new Entry(t.getCreatedAt(), "ASSIGNMENT", actor, assignee(d), null);
            case "NOTE_ADDED" -> null; // the note itself is listed from the notes table
            case "NOTE_EDITED" -> new Entry(t.getCreatedAt(), "NOTE", actor, "Note edited", null);
            case "NOTE_DELETED" -> new Entry(t.getCreatedAt(), "NOTE", actor, "Note deleted", null);
            default -> new Entry(t.getCreatedAt(), "SYSTEM", actor, type.replace('_', ' ').toLowerCase(), t.getDetail());
        };
    }

    private String assignee(JsonNode d) {
        JsonNode id = d.path("assigneeId");
        if (id.isMissingNode() || id.isNull()) {
            return "Unassigned";
        }
        return "Assigned to " + userRepository.findById(id.asLong()).map(u -> u.getUsername()).orElse("user #" + id.asLong());
    }

    /** UI wording for statuses (New / In Progress for the first two). */
    static String label(String status) {
        return switch (status) {
            case "OPEN" -> "New";
            case "INVESTIGATING" -> "In Progress";
            case "FALSE_POSITIVE" -> "False Positive";
            default -> status.isEmpty() ? "?" : status.charAt(0) + status.substring(1).toLowerCase();
        };
    }

    private String summary(AiAnalysis a) {
        JsonNode out = json(a.getOutput());
        String s = out.path("summary").asText(null);
        return s == null ? null : (s.length() > 400 ? s.substring(0, 400) + "…" : s);
    }

    private JsonNode json(String raw) {
        try {
            return raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }
}
