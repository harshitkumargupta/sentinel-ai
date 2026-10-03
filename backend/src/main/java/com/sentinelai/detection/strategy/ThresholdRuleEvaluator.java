package com.sentinelai.detection.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.engine.GroupBy;
import com.sentinelai.event.domain.SecurityEvent;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Generic threshold rule: fires when {@code >= threshold} events of {@code eventType} occur for
 * the same {@code groupBy} value within {@code windowSeconds}.
 *
 * <p>config: {@code {"eventType":"FAILED_LOGIN","threshold":10,"windowSeconds":300,"groupBy":"username"}}
 */
@Component
public class ThresholdRuleEvaluator extends AbstractThresholdEvaluator {

    public ThresholdRuleEvaluator(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String type() {
        return "THRESHOLD";
    }

    @Override
    protected Optional<Params> params(SecurityEvent event, DetectionRule rule) {
        JsonNode config = config(rule);
        int threshold = config.path("threshold").asInt(0);
        int windowSeconds = config.path("windowSeconds").asInt(0);
        GroupBy groupBy = GroupBy.fromString(config.path("groupBy").asText(null), GroupBy.USERNAME);
        return Optional.of(new Params(
                eventTypeOrNull(config, event.getEventType()), threshold, windowSeconds, groupBy));
    }
}
