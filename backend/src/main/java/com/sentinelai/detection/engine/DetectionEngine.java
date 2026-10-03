package com.sentinelai.detection.engine;

import com.sentinelai.audit.service.AuditService;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.detection.config.DetectionProperties;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.event.SecurityEventCreatedEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentEvent;
import com.sentinelai.incident.domain.IncidentEventId;
import com.sentinelai.incident.domain.IncidentFeedback;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Evaluates enabled detection rules against each newly ingested event and creates or correlates
 * incidents. Driven by {@link SecurityEventCreatedEvent} so the event module stays decoupled from
 * detection. One misbehaving rule never blocks the others, and the whole engine is gated by the
 * {@code sentinel.detection.enabled} flag.
 */
@Slf4j
@Service
public class DetectionEngine {

    private static final List<IncidentStatus> ACTIVE_STATUSES =
            List.of(IncidentStatus.OPEN, IncidentStatus.INVESTIGATING, IncidentStatus.CONTAINED);

    private final Map<String, DetectionStrategy> strategies;
    private final DetectionRuleRepository ruleRepository;
    private final SecurityEventRepository eventRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final AuditService auditService;
    private final DetectionContext context;
    private final DetectionProperties properties;

    public DetectionEngine(List<DetectionStrategy> strategies,
                           DetectionRuleRepository ruleRepository,
                           SecurityEventRepository eventRepository,
                           IncidentRepository incidentRepository,
                           IncidentEventRepository incidentEventRepository,
                           AuditService auditService,
                           DetectionContext context,
                           DetectionProperties properties) {
        this.strategies = strategies.stream()
                .collect(Collectors.toMap(DetectionStrategy::type, Function.identity()));
        this.ruleRepository = ruleRepository;
        this.eventRepository = eventRepository;
        this.incidentRepository = incidentRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.auditService = auditService;
        this.context = context;
        this.properties = properties;
        log.info("Detection engine initialized with strategies: {}", this.strategies.keySet());
    }

    @EventListener
    @Transactional
    public void onSecurityEvent(SecurityEventCreatedEvent created) {
        if (!properties.isEnabled()) {
            return;
        }
        SecurityEvent event = eventRepository.findById(created.eventId()).orElse(null);
        if (event == null) {
            return;
        }
        for (DetectionRule rule : ruleRepository.findByOrg_IdAndEnabledTrue(event.getOrg().getId())) {
            DetectionStrategy strategy = strategies.get(rule.getRuleType());
            if (strategy == null) {
                log.debug("No strategy for rule type {} (rule {})", rule.getRuleType(), rule.getId());
                continue;
            }
            try {
                strategy.evaluate(event, rule, context)
                        .ifPresent(outcome -> applyOutcome(event, rule, outcome));
            } catch (Exception ex) {
                // A single faulty rule must not break evaluation of the others.
                log.error("Detection rule {} failed to evaluate event {}", rule.getId(), event.getId(), ex);
            }
        }
    }

    private void applyOutcome(SecurityEvent event, DetectionRule rule, DetectionOutcome outcome) {
        Long orgId = event.getOrg().getId();
        Incident incident = incidentRepository
                .findFirstByOrg_IdAndCorrelationKeyAndStatusInOrderByIdDesc(
                        orgId, outcome.correlationKey(), ACTIVE_STATUSES)
                .orElse(null);

        boolean created = incident == null;
        if (created) {
            Organization org = event.getOrg();
            incident = incidentRepository.save(Incident.builder()
                    .org(org)
                    .title(outcome.title())
                    .description(outcome.reason())
                    .status(IncidentStatus.OPEN)
                    .severity(rule.getSeverity())
                    .riskScore(properties.scoreFor(rule.getSeverity()))
                    .riskBreakdown(outcome.reason())
                    .feedback(IncidentFeedback.UNREVIEWED)
                    .correlationKey(outcome.correlationKey())
                    .build());
        }

        int linked = linkEvents(incident, event, outcome.matchedEvents());
        if (!created && linked > 0) {
            incidentRepository.save(incident); // bump updated_at
        }

        auditService.record(orgId, null,
                created ? "INCIDENT_CREATE" : "INCIDENT_CORRELATE",
                "incident", incident.getId(), outcome.reason(), "self");
    }

    private int linkEvents(Incident incident, SecurityEvent trigger, List<SecurityEvent> matched) {
        // De-duplicate, keep the triggering event first, cap at the configured maximum.
        Set<SecurityEvent> ordered = new LinkedHashSet<>();
        ordered.add(trigger);
        ordered.addAll(matched);

        int linked = 0;
        for (SecurityEvent ev : ordered) {
            if (linked >= properties.getMaxLinkedEvents()) {
                break;
            }
            IncidentEventId id = new IncidentEventId(incident.getId(), ev.getId());
            if (!incidentEventRepository.existsById(id)) {
                incidentEventRepository.save(new IncidentEvent(incident, ev));
                linked++;
            }
        }
        return linked;
    }
}
