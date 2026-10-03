package com.sentinelai.detection.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.engine.GroupBy;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * API-abuse / rate-limit rule: a fixed 60-second window keyed by source IP by default.
 *
 * <p>config: {@code {"eventType":"API_ABUSE","requestsPerMinute":1000,"groupBy":"sourceIp"}}
 */
@Component
public class RateLimitRuleEvaluator extends AbstractThresholdEvaluator {

    private static final int ONE_MINUTE = 60;

    public RateLimitRuleEvaluator(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String type() {
        return "RATE_LIMIT";
    }

    @Override
    protected Optional<Params> params(SecurityEvent event, DetectionRule rule) {
        JsonNode config = config(rule);
        int threshold = config.path("requestsPerMinute").asInt(0);
        GroupBy groupBy = GroupBy.fromString(config.path("groupBy").asText(null), GroupBy.SOURCE_IP);
        return Optional.of(new Params(
                eventTypeOrNull(config, EventType.API_ABUSE), threshold, ONE_MINUTE, groupBy));
    }
}
