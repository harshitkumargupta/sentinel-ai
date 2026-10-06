package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.engine.AlertDraft;
import com.sentinelai.detection.engine.ConfigReader;
import com.sentinelai.detection.engine.DetectionRuleEvaluator;
import com.sentinelai.detection.engine.GroupBy;
import com.sentinelai.detection.engine.RuleContext;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * High-frequency API access: ≥ {@code threshold} API_ABUSE events from one source IP within
 * {@code windowSeconds}. config: {@code {threshold, windowSeconds, groupBy}}.
 */
@Component
@RequiredArgsConstructor
public class HighFrequencyApiRule implements DetectionRuleEvaluator {

    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return RuleTypes.HIGH_FREQUENCY_API;
    }

    @Override
    public Optional<AlertDraft> evaluate(SecurityEvent event, DetectionRule rule, RuleContext ctx) {
        if (event.getEventType() != EventType.API_ABUSE) {
            return Optional.empty();
        }
        ConfigReader cfg = ConfigReader.of(rule.getConfig(), objectMapper);
        int threshold = cfg.getInt("threshold", 100);
        int window = cfg.getInt("windowSeconds", 60);
        GroupBy by = GroupBy.fromString(cfg.getString("groupBy", "sourceIp"), GroupBy.SOURCE_IP);

        String value = by.valueOf(event);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Long orgId = event.getOrg().getId();
        long count = ctx.countInWindow(orgId, EventType.API_ABUSE, by, value, window);
        if (count < threshold) {
            return Optional.empty();
        }
        List<Long> matched = ctx.recentEventIds(orgId, EventType.API_ABUSE, by, value, window, 100);
        String message = "High-frequency API: %d requests for %s=%s within %ds"
                .formatted(count, by, value, window);
        return Optional.of(new AlertDraft(
                rule.getSeverity(), rule.getMitreTechnique(), message, by + ":" + value, matched));
    }
}
