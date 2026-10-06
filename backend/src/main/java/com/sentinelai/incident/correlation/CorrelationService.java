package com.sentinelai.incident.correlation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.alert.domain.Alert;
import com.sentinelai.cache.IncidentsChangedEvent;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentAlert;
import com.sentinelai.incident.domain.IncidentEvent;
import com.sentinelai.incident.domain.IncidentEventId;
import com.sentinelai.incident.domain.IncidentFeedback;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.incident.repository.IncidentAlertRepository;
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.notification.NotificationService;
import com.sentinelai.risk.RiskResult;
import com.sentinelai.risk.RiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link Correlator}: groups alerts into incidents by entity (user/IP) within a time window,
 * chaining related rule types for the same entity. Idempotent (composite-key links), transactional,
 * and rescoring on every join with auto-escalation + notifications for HIGH/CRITICAL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorrelationService implements Correlator {

    private static final List<IncidentStatus> ACTIVE =
            List.of(IncidentStatus.OPEN, IncidentStatus.INVESTIGATING, IncidentStatus.CONTAINED);

    private final IncidentRepository incidentRepository;
    private final IncidentAlertRepository incidentAlertRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final SecurityEventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final NotificationService notificationService;
    private final RiskService riskService;
    private final TimelineService timeline;
    private final CorrelationProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    /** Result of correlating one alert, for callers that must act on the outcome (e.g. Kafka). */
    public record CorrelationOutcome(Incident incident, boolean created, boolean escalated) {
    }

    @Override
    @Transactional
    public Incident correlate(Alert alert) {
        // Synchronous path: correlate and notify inline.
        return doCorrelate(alert, true).incident();
    }

    /**
     * Correlate for the Kafka pipeline: notifications are <em>not</em> sent inline — the notification
     * consumer raises them from {@code incidents.updates} instead — and the escalation outcome is
     * returned so the caller can publish an incident update.
     */
    @Transactional
    public CorrelationOutcome correlateForPipeline(Alert alert) {
        return doCorrelate(alert, false);
    }

    private CorrelationOutcome doCorrelate(Alert alert, boolean notify) {
        if (!properties.isEnabled()) {
            return new CorrelationOutcome(null, false, false);
        }
        // Idempotency: an alert belongs to at most one incident.
        if (incidentAlertRepository.existsById_AlertId(alert.getId())) {
            return new CorrelationOutcome(null, false, false);
        }

        Long orgId = alert.getOrg().getId();
        SecurityEvent trigger = alert.getTriggeringEventId() == null ? null
                : eventRepository.findById(alert.getTriggeringEventId()).orElse(null);
        Instant alertTime = trigger != null ? trigger.getEventTimestamp() : clock.instant();
        String key = deriveKey(trigger, alert);

        // Correlation window is measured in EVENT time: an alert joins the most recent active
        // incident for the same entity only if it is within the window of that incident's last
        // event. A larger gap starts a new incident (distinct attack episode).
        Incident incident = incidentRepository
                .findFirstByOrg_IdAndCorrelationKeyAndStatusInOrderByIdDesc(orgId, key, ACTIVE)
                .filter(i -> {
                    Instant last = latestEventTime(i.getId());
                    return last == null
                            || Math.abs(Duration.between(last, alertTime).getSeconds()) <= properties.getWindowSeconds();
                })
                .orElse(null);

        boolean created = incident == null;
        Severity oldSeverity = created ? null : incident.getSeverity();
        if (created) {
            incident = incidentRepository.save(Incident.builder()
                    .org(organizationRepository.getReferenceById(orgId))
                    .title(truncate(alert.getMessage()))
                    .status(IncidentStatus.OPEN)
                    .severity(alert.getSeverity())
                    .feedback(IncidentFeedback.UNREVIEWED)
                    .correlationKey(key)
                    .riskScore(0)
                    .build());
            timeline.record(incident.getId(), TimelineService.INCIDENT_CREATED, "system",
                    "{\"correlationKey\":\"" + key + "\"}");
        }

        // Link the alert and its events (idempotent via composite keys).
        incidentAlertRepository.save(new IncidentAlert(incident, alert));
        linkEvents(incident, alert);
        timeline.record(incident.getId(), TimelineService.ALERT_JOINED, "system",
                "{\"alertId\":" + alert.getId() + ",\"ruleType\":\"" + alert.getRuleType() + "\"}");

        rescore(incident);

        boolean escalated = incident.getSeverity().ordinal() >= Severity.HIGH.ordinal()
                && (created || (oldSeverity != null && incident.getSeverity().ordinal() > oldSeverity.ordinal()));
        if (escalated) {
            escalate(incident, notify);
        }
        events.publishEvent(new IncidentsChangedEvent(orgId));
        return new CorrelationOutcome(incident, created, escalated);
    }

    private void linkEvents(Incident incident, Alert alert) {
        List<Long> eventIds = new ArrayList<>();
        if (alert.getTriggeringEventId() != null) {
            eventIds.add(alert.getTriggeringEventId());
        }
        if (alert.getMatchedEventIds() != null) {
            try {
                for (Long id : objectMapper.readValue(alert.getMatchedEventIds(), Long[].class)) {
                    eventIds.add(id);
                }
            } catch (Exception ignored) {
                // tolerate malformed json
            }
        }
        for (Long eventId : eventIds) {
            IncidentEventId id = new IncidentEventId(incident.getId(), eventId);
            if (!incidentEventRepository.existsById(id)) {
                SecurityEvent ref = eventRepository.getReferenceById(eventId);
                incidentEventRepository.save(new IncidentEvent(incident, ref));
            }
        }
    }

    private void rescore(Incident incident) {
        List<Alert> alerts = incidentAlertRepository.findById_IncidentId(incident.getId()).stream()
                .map(IncidentAlert::getAlert).toList();
        List<SecurityEvent> events = incidentEventRepository.findById_IncidentId(incident.getId()).stream()
                .map(IncidentEvent::getEvent).toList();

        RiskResult risk = riskService.score(alerts, events);
        incident.setRiskScore(risk.score());
        incident.setSeverity(risk.severity());
        incident.setRiskBreakdown(toJson(risk));
        incidentRepository.save(incident);
        timeline.record(incident.getId(), TimelineService.RESCORED, "system",
                "{\"score\":" + risk.score() + ",\"severity\":\"" + risk.severity() + "\"}");
    }

    private void escalate(Incident incident, boolean notify) {
        timeline.record(incident.getId(), TimelineService.ESCALATED, "system",
                "{\"severity\":\"" + incident.getSeverity() + "\"}");
        if (!notify) {
            // Kafka pipeline: the notification consumer raises notifications from incidents.updates.
            return;
        }
        notificationService.notifyAdminsOfEscalation(incident.getId(), incident.getSeverity(),
                incident.getRiskScore());
    }

    private String deriveKey(SecurityEvent trigger, Alert alert) {
        if (trigger != null) {
            if (trigger.getUsername() != null) {
                return "user:" + trigger.getUsername();
            }
            if (trigger.getSourceIp() != null) {
                return "ip:" + trigger.getSourceIp();
            }
        }
        return alert.getEntityKey() != null ? alert.getEntityKey() : "alert:" + alert.getId();
    }

    private Instant latestEventTime(Long incidentId) {
        return incidentEventRepository.findById_IncidentId(incidentId).stream()
                .map(ie -> ie.getEvent().getEventTimestamp())
                .max(Instant::compareTo)
                .orElse(null);
    }

    private String toJson(RiskResult risk) {
        try {
            return objectMapper.writeValueAsString(risk);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "Incident";
        }
        return s.length() > 255 ? s.substring(0, 255) : s;
    }
}
