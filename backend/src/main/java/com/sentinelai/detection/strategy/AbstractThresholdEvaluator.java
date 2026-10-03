package com.sentinelai.detection.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.engine.DetectionContext;
import com.sentinelai.detection.engine.DetectionOutcome;
import com.sentinelai.detection.engine.DetectionStrategy;
import com.sentinelai.detection.engine.GroupBy;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared "N events in a time window, grouped by an attribute" detection. Concrete strategies
 * supply their parameters from the rule's {@code config} JSON; the counting/firing logic lives
 * here so adding a new count-based rule type is just a small subclass.
 */
@Slf4j
public abstract class AbstractThresholdEvaluator implements DetectionStrategy {

    protected final ObjectMapper objectMapper;

    protected AbstractThresholdEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Effective parameters for this rule, or empty if the config is invalid/not applicable. */
    protected abstract Optional<Params> params(SecurityEvent event, DetectionRule rule);

    @Override
    public final Optional<DetectionOutcome> evaluate(SecurityEvent event, DetectionRule rule,
                                                     DetectionContext ctx) {
        Optional<Params> maybe = params(event, rule);
        if (maybe.isEmpty()) {
            return Optional.empty();
        }
        Params p = maybe.get();
        if (p.threshold() < 1 || p.windowSeconds() < 1) {
            log.warn("Rule {} has invalid threshold/window; skipping", rule.getId());
            return Optional.empty();
        }
        if (event.getEventType() != p.eventType()) {
            return Optional.empty();
        }
        String value = p.groupBy().valueOf(event);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        Instant since = ctx.now().minusSeconds(p.windowSeconds());
        long count = ctx.countInWindow(event.getOrg().getId(), p.eventType(), p.groupBy(), value, since);
        if (count < p.threshold()) {
            return Optional.empty();
        }

        int fetch = (int) Math.min(count, 1000);
        List<SecurityEvent> matched =
                ctx.eventsInWindow(event.getOrg().getId(), p.eventType(), p.groupBy(), value, since, fetch);

        String correlationKey = "rule:" + rule.getId() + ":" + p.groupBy() + ":" + value;
        String title = "%s: %d %s events for %s=%s in %ds"
                .formatted(rule.getName(), count, p.eventType(), p.groupBy(), value, p.windowSeconds());
        String reason = reason(p, value, count);
        return Optional.of(new DetectionOutcome(correlationKey, title, matched, reason));
    }

    protected JsonNode config(DetectionRule rule) {
        try {
            if (rule.getConfig() == null || rule.getConfig().isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(rule.getConfig());
        } catch (Exception e) {
            log.warn("Rule {} has unparseable config JSON; skipping", rule.getId());
            return objectMapper.createObjectNode();
        }
    }

    protected EventType eventTypeOrNull(JsonNode config, EventType fallback) {
        JsonNode node = config.get("eventType");
        if (node == null || node.isNull()) {
            return fallback;
        }
        try {
            return EventType.valueOf(node.asText());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private String reason(Params p, String value, long count) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", type(),
                    "eventType", p.eventType().name(),
                    "threshold", p.threshold(),
                    "windowSeconds", p.windowSeconds(),
                    "groupBy", p.groupBy().name(),
                    "value", value,
                    "count", count));
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Effective, resolved parameters for a single evaluation. */
    public record Params(EventType eventType, int threshold, int windowSeconds, GroupBy groupBy) {
    }
}
