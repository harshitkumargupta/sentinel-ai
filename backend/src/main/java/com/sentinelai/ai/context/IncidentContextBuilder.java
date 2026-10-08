package com.sentinelai.ai.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.ai.AiProperties;
import com.sentinelai.ai.security.InjectionDetector;
import com.sentinelai.ai.security.PiiRedactor;
import com.sentinelai.ai.security.PromptSanitizer;
import com.sentinelai.common.util.Hashing;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.repository.IncidentAlertRepository;
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.incident.repository.IncidentTimelineRepository;
import com.sentinelai.risk.RiskResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a capped, sanitized {@link IncidentContext} for an incident. Caps the number of events and
 * timeline entries, truncates and strips control characters from every free-text field, redacts PII,
 * and never includes raw payloads, secrets or hashes. Also scans the (pre-sanitization) event data
 * for prompt-injection attempts so the caller can flag the incident.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentContextBuilder {

    private final IncidentEventRepository incidentEventRepository;
    private final IncidentAlertRepository incidentAlertRepository;
    private final IncidentTimelineRepository timelineRepository;
    private final AiProperties props;
    private final PromptSanitizer sanitizer;
    private final PiiRedactor redactor;
    private final InjectionDetector injectionDetector;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public BuiltContext build(Incident incident) {
        AiProperties.ContextCaps caps = props.getContext();
        Long orgId = incident.getOrg().getId();

        List<SecurityEvent> events = incidentEventRepository.findById_IncidentId(incident.getId()).stream()
                .map(l -> l.getEvent())
                .sorted(Comparator.comparing(SecurityEvent::getEventTimestamp))
                .limit(caps.getMaxEvents())
                .toList();

        // Injection scan over attacker-controlled fields (before sanitization neutralizes them).
        List<String> injectionHits = new ArrayList<>();
        for (SecurityEvent e : events) {
            injectionHits.addAll(injectionDetector.detect(e.getUserAgent()));
            injectionHits.addAll(injectionDetector.detect(e.getRawPayload()));
            injectionHits.addAll(injectionDetector.detect(e.getResource()));
        }

        List<IncidentContext.EventSummary> eventSummaries = new ArrayList<>();
        List<Long> eventIds = new ArrayList<>();
        for (SecurityEvent e : events) {
            eventIds.add(e.getId());
            eventSummaries.add(new IncidentContext.EventSummary(
                    e.getId(),
                    e.getEventType() == null ? null : e.getEventType().name(),
                    e.getSeverity() == null ? null : e.getSeverity().name(),
                    e.getSourceIp(),
                    field(e.getUsername()),
                    field(e.getResource()),
                    field(e.getUserAgent()),
                    e.getGeoCountry(),
                    e.getEventTimestamp() == null ? null : e.getEventTimestamp().toString(),
                    field(hostOf(e))));
        }

        // MITRE tags from the incident's alerts.
        Set<String> mitre = new LinkedHashSet<>();
        incidentAlertRepository.findById_IncidentId(incident.getId()).forEach(l -> {
            String t = l.getAlert().getMitreTechnique();
            if (t != null && !t.isBlank()) {
                mitre.add(t);
            }
        });

        List<IncidentContext.RiskFactor> factors = parseFactors(incident.getRiskBreakdown());

        List<IncidentContext.TimelineItem> timeline =
                timelineRepository.findByIncidentIdOrderByIdAsc(incident.getId()).stream()
                        .limit(caps.getMaxTimelineEntries())
                        .map(t -> new IncidentContext.TimelineItem(
                                t.getType(), field(t.getActor()),
                                t.getCreatedAt() == null ? null : t.getCreatedAt().toString()))
                        .toList();

        IncidentContext.Entity entity = deriveEntity(incident, events);

        IncidentContext context = new IncidentContext(
                incident.getId(), orgId,
                incident.getSeverity() == null ? null : incident.getSeverity().name(),
                incident.getRiskScore(), entity, new ArrayList<>(mitre), factors,
                eventSummaries, timeline, eventIds);

        return new BuiltContext(context, hash(context), injectionHits);
    }

    private static String hostOf(SecurityEvent e) {
        String key = e.getEntityKey();
        return key != null && key.startsWith("host:") ? key.substring("host:".length()) : null;
    }

    private String field(String raw) {
        if (raw == null) {
            return null;
        }
        return sanitizer.sanitizeField(redactor.redact(raw), props.getContext().getMaxFieldChars());
    }

    private List<IncidentContext.RiskFactor> parseFactors(String riskBreakdownJson) {
        List<IncidentContext.RiskFactor> out = new ArrayList<>();
        if (riskBreakdownJson == null || riskBreakdownJson.isBlank()) {
            return out;
        }
        try {
            RiskResult risk = objectMapper.readValue(riskBreakdownJson, RiskResult.class);
            if (risk.breakdown() != null) {
                risk.breakdown().stream()
                        .filter(f -> f.points() > 0)
                        .forEach(f -> out.add(new IncidentContext.RiskFactor(
                                f.name(), f.points(), field(f.reason()))));
            }
        } catch (Exception e) {
            log.debug("Could not parse risk breakdown for context: {}", e.toString());
        }
        return out;
    }

    private IncidentContext.Entity deriveEntity(Incident incident, List<SecurityEvent> events) {
        String key = incident.getCorrelationKey();
        if (key != null && key.contains(":")) {
            String[] parts = key.split(":", 2);
            return new IncidentContext.Entity(parts[0], parts[1]);
        }
        for (SecurityEvent e : events) {
            if (e.getUsername() != null) {
                return new IncidentContext.Entity("user", e.getUsername());
            }
            if (e.getSourceIp() != null) {
                return new IncidentContext.Entity("ip", e.getSourceIp());
            }
        }
        return new IncidentContext.Entity("unknown", null);
    }

    /**
     * Hash a stable projection of the context — identity, severity, risk, entity, events, MITRE and
     * risk factors — but NOT the timeline, which grows with each investigation. This keeps
     * re-investigation of an unchanged incident idempotent.
     */
    private String hash(IncidentContext context) {
        StringBuilder sb = new StringBuilder()
                .append(context.incidentId()).append('|')
                .append(context.severity()).append('|')
                .append(context.riskScore()).append('|')
                .append(context.entity()).append('|')
                .append(context.eventIds()).append('|')
                .append(context.mitre()).append('|');
        context.riskFactors().forEach(f -> sb.append(f.name()).append('=').append(f.points()).append(','));
        return Hashing.sha256Hex(sb.toString());
    }
}
