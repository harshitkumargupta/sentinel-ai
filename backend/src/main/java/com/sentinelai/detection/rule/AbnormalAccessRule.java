package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.engine.AlertDraft;
import com.sentinelai.detection.engine.ConfigReader;
import com.sentinelai.detection.engine.DetectionRuleEvaluator;
import com.sentinelai.detection.engine.RuleContext;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Abnormal access: access to a sensitive resource outside the actor's normal rights (e.g. a viewer
 * hitting an admin endpoint). Fires on ABNORMAL_ACCESS events, optionally filtered to a resource
 * prefix. config: {@code {resourcePrefix}} (optional).
 */
@Component
@RequiredArgsConstructor
public class AbnormalAccessRule implements DetectionRuleEvaluator {

    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return RuleTypes.ABNORMAL_ACCESS;
    }

    @Override
    public Optional<AlertDraft> evaluate(SecurityEvent event, DetectionRule rule, RuleContext ctx) {
        if (event.getEventType() != EventType.ABNORMAL_ACCESS) {
            return Optional.empty();
        }
        ConfigReader cfg = ConfigReader.of(rule.getConfig(), objectMapper);
        String prefix = cfg.getString("resourcePrefix", null);
        if (prefix != null && (event.getResource() == null || !event.getResource().startsWith(prefix))) {
            return Optional.empty();
        }
        String entity = event.getUsername() != null ? "user:" + event.getUsername()
                : "resource:" + event.getResource();
        String message = "Abnormal access by %s to %s"
                .formatted(event.getUsername(), event.getResource());
        return Optional.of(new AlertDraft(
                rule.getSeverity(), rule.getMitreTechnique(), message, entity, List.of(event.getId())));
    }
}
