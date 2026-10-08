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

import java.util.List;
import java.util.Optional;

/**
 * Shared logic for "N events of one type for the same entity within a window" rules. Fires when the
 * count first reaches {@code threshold} and again at each further multiple, so a long burst yields a
 * handful of alerts instead of one per event. config: {@code {threshold, windowSeconds, groupBy}};
 * subclasses supply the event type, defaults and message label.
 */
public abstract class EventCountRule implements DetectionRuleEvaluator {

    private final ObjectMapper objectMapper;

    protected EventCountRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected abstract EventType eventType();

    protected abstract String label();

    protected abstract int defaultThreshold();

    protected abstract int defaultWindowSeconds();

    protected abstract GroupBy defaultGroupBy();

    /** Extra per-event filter (e.g. a minimum transfer size); default accepts every event. */
    protected boolean matches(SecurityEvent event, ConfigReader cfg) {
        return true;
    }

    @Override
    public Optional<AlertDraft> evaluate(SecurityEvent event, DetectionRule rule, RuleContext ctx) {
        if (event.getEventType() != eventType()) {
            return Optional.empty();
        }
        ConfigReader cfg = ConfigReader.of(rule.getConfig(), objectMapper);
        if (!matches(event, cfg)) {
            return Optional.empty();
        }
        int threshold = Math.max(1, cfg.getInt("threshold", defaultThreshold()));
        int window = cfg.getInt("windowSeconds", defaultWindowSeconds());
        GroupBy by = GroupBy.fromString(cfg.getString("groupBy", null), defaultGroupBy());

        String value = by.valueOf(event);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Long orgId = event.getOrg().getId();
        long count = ctx.countInWindow(orgId, eventType(), by, value, window);
        if (count < threshold || count % threshold != 0) {
            return Optional.empty();
        }
        List<Long> matched = ctx.recentEventIds(orgId, eventType(), by, value, window,
                Math.min(threshold * 2, 100));
        String message = "%s: %d event(s) for %s=%s within %ds".formatted(label(), count, by, value, window);
        return Optional.of(new AlertDraft(
                rule.getSeverity(), rule.getMitreTechnique(), message, by + ":" + value, matched));
    }
}
