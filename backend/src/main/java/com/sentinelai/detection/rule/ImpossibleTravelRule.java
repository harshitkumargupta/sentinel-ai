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

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Impossible travel: a login from a different country than the user's previous login, too soon to
 * have physically travelled. config: {@code {minSecondsBetweenCountries}} (anything faster fires).
 */
@Component
@RequiredArgsConstructor
public class ImpossibleTravelRule implements DetectionRuleEvaluator {

    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return RuleTypes.IMPOSSIBLE_TRAVEL;
    }

    @Override
    public Optional<AlertDraft> evaluate(SecurityEvent event, DetectionRule rule, RuleContext ctx) {
        if (event.getEventType() != EventType.SUSPICIOUS_LOGIN
                || event.getUsername() == null || event.getGeoCountry() == null) {
            return Optional.empty();
        }
        ConfigReader cfg = ConfigReader.of(rule.getConfig(), objectMapper);
        int minSeconds = cfg.getInt("minSecondsBetweenCountries", 3600);

        Optional<SecurityEvent> prevOpt =
                ctx.previousUserEvent(event.getOrg().getId(), event.getUsername(), event.getId());
        if (prevOpt.isEmpty()) {
            return Optional.empty();
        }
        SecurityEvent prev = prevOpt.get();
        boolean differentCountry = !event.getGeoCountry().equalsIgnoreCase(prev.getGeoCountry());
        long gap = Duration.between(prev.getEventTimestamp(), event.getEventTimestamp()).abs().getSeconds();
        if (!differentCountry || gap > minSeconds) {
            return Optional.empty();
        }
        String message = "Impossible travel for %s: %s -> %s in %ds"
                .formatted(event.getUsername(), prev.getGeoCountry(), event.getGeoCountry(), gap);
        return Optional.of(new AlertDraft(
                rule.getSeverity(), rule.getMitreTechnique(), message,
                "user:" + event.getUsername(), List.of(prev.getId(), event.getId())));
    }
}
