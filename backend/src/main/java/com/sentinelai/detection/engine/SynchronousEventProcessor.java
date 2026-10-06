package com.sentinelai.detection.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.alert.domain.Alert;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.detection.config.DetectionProperties;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.event.SecurityEventCreatedEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.incident.correlation.Correlator;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs enabled rules against each ingested event and persists any resulting alerts. One failing
 * rule is caught, logged (with the request trace id in MDC) and counted — it never stops the other
 * rules. Gated by {@code sentinel.detection.enabled}.
 */
@Slf4j
@Service
public class SynchronousEventProcessor implements EventProcessor {

    private final Map<String, DetectionRuleEvaluator> rules;
    private final DetectionRuleRepository ruleRepository;
    private final SecurityEventRepository eventRepository;
    private final AlertRepository alertRepository;
    private final OrganizationRepository organizationRepository;
    private final LiveRuleContext ruleContext;
    private final DetectionProperties properties;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final Correlator correlator;

    public SynchronousEventProcessor(List<DetectionRuleEvaluator> ruleBeans,
                                     DetectionRuleRepository ruleRepository,
                                     SecurityEventRepository eventRepository,
                                     AlertRepository alertRepository,
                                     OrganizationRepository organizationRepository,
                                     LiveRuleContext ruleContext,
                                     DetectionProperties properties,
                                     MeterRegistry meterRegistry,
                                     ObjectMapper objectMapper,
                                     Correlator correlator) {
        this.rules = ruleBeans.stream()
                .collect(Collectors.toMap(DetectionRuleEvaluator::type, Function.identity()));
        this.ruleRepository = ruleRepository;
        this.eventRepository = eventRepository;
        this.alertRepository = alertRepository;
        this.organizationRepository = organizationRepository;
        this.ruleContext = ruleContext;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.correlator = correlator;
        log.info("Detection engine initialized with rules: {}", this.rules.keySet());
    }

    @EventListener
    @Transactional
    public void onSecurityEvent(SecurityEventCreatedEvent created) {
        if (!properties.isEnabled()) {
            return;
        }
        eventRepository.findById(created.eventId()).ifPresent(this::process);
    }

    @Override
    public void process(SecurityEvent event) {
        for (Alert alert : runRules(event)) {
            // Correlate each alert into a (new or existing) incident.
            correlator.correlate(alert);
        }
    }

    /**
     * Runs the enabled rules against one event and persists any resulting alerts, <em>without</em>
     * correlating them. The synchronous path then correlates inline; the Kafka detection consumer
     * publishes the alerts to the {@code alerts} topic for the correlation consumer instead. One
     * failing rule is caught, logged and counted — it never stops the other rules.
     */
    public List<Alert> runRules(SecurityEvent event) {
        ruleContext.observe(event);
        String runId = RunContextHolder.get();
        Long orgId = event.getOrg().getId();
        List<Alert> saved = new ArrayList<>();

        for (DetectionRule rule : ruleRepository.findByOrg_IdAndEnabledTrue(orgId)) {
            DetectionRuleEvaluator evaluator = rules.get(rule.getRuleType());
            if (evaluator == null) {
                continue;
            }
            try {
                evaluator.evaluate(event, rule, ruleContext)
                        .ifPresent(draft -> saved.add(persistAlert(event, rule, draft, runId)));
            } catch (Exception ex) {
                meterRegistry.counter("sentinel.detection.rule_errors", "rule_type", rule.getRuleType())
                        .increment();
                log.error("Detection rule {} ({}) failed on event {}",
                        rule.getId(), rule.getRuleType(), event.getId(), ex);
            }
        }
        return saved;
    }

    private Alert persistAlert(SecurityEvent event, DetectionRule rule, AlertDraft draft, String runId) {
        Alert alert = alertRepository.save(Alert.builder()
                .org(organizationRepository.getReferenceById(event.getOrg().getId()))
                .ruleId(rule.getId())
                .ruleVersion(rule.getVersion())
                .ruleType(rule.getRuleType())
                .severity(draft.severity())
                .mitreTechnique(draft.mitreTechnique())
                .message(truncate(draft.message()))
                .entityKey(draft.entityKey())
                .triggeringEventId(event.getId())
                .matchedEventIds(toJson(draft.matchedEventIds()))
                .runId(runId)
                .build());
        meterRegistry.counter("sentinel.detection.alerts", "rule_type", rule.getRuleType()).increment();
        return alert;
    }

    private String toJson(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String truncate(String s) {
        return s != null && s.length() > 500 ? s.substring(0, 500) : s;
    }
}
