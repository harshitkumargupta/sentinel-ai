package com.sentinelai.detection.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.engine.DetectionContext;
import com.sentinelai.detection.engine.DetectionOutcome;
import com.sentinelai.detection.engine.DetectionStrategy;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Fires when a user logs in from a different country than their most recent prior login
 * ("new geo" — a simple stand-in for impossible-travel until real geo distance is available).
 *
 * <p>config: {@code {"eventType":"SUSPICIOUS_LOGIN"}}
 */
@Slf4j
@Component
public class GeoVelocityRuleEvaluator implements DetectionStrategy {

    private final ObjectMapper objectMapper;

    public GeoVelocityRuleEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "GEO_VELOCITY";
    }

    @Override
    public Optional<DetectionOutcome> evaluate(SecurityEvent event, DetectionRule rule,
                                               DetectionContext ctx) {
        EventType expected = eventType(rule);
        if (event.getEventType() != expected
                || event.getUsername() == null
                || event.getGeoCountry() == null) {
            return Optional.empty();
        }

        Optional<SecurityEvent> prior =
                ctx.latestForUserBefore(event.getOrg().getId(), event.getUsername(), event.getId());
        if (prior.isEmpty() || event.getGeoCountry().equalsIgnoreCase(prior.get().getGeoCountry())) {
            return Optional.empty();
        }

        SecurityEvent previous = prior.get();
        String correlationKey = "rule:" + rule.getId() + ":geo:" + event.getUsername();
        String title = "Suspicious login: new country %s (was %s) for %s"
                .formatted(event.getGeoCountry(), previous.getGeoCountry(), event.getUsername());
        return Optional.of(new DetectionOutcome(
                correlationKey, title, List.of(previous, event),
                "{\"type\":\"GEO_VELOCITY\",\"from\":\"" + previous.getGeoCountry()
                        + "\",\"to\":\"" + event.getGeoCountry() + "\"}"));
    }

    private EventType eventType(DetectionRule rule) {
        try {
            if (rule.getConfig() == null || rule.getConfig().isBlank()) {
                return EventType.SUSPICIOUS_LOGIN;
            }
            JsonNode node = objectMapper.readTree(rule.getConfig()).get("eventType");
            return node == null ? EventType.SUSPICIOUS_LOGIN : EventType.valueOf(node.asText());
        } catch (Exception e) {
            return EventType.SUSPICIOUS_LOGIN;
        }
    }
}
