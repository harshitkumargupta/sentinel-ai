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

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Suspicious login: a successful/flagged login from a new country or at an odd hour (UTC).
 * config: {@code {oddHourStart, oddHourEnd}} (half-open [start, end) UTC hours).
 */
@Component
@RequiredArgsConstructor
public class SuspiciousLoginRule implements DetectionRuleEvaluator {

    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return RuleTypes.SUSPICIOUS_LOGIN;
    }

    @Override
    public Optional<AlertDraft> evaluate(SecurityEvent event, DetectionRule rule, RuleContext ctx) {
        if (event.getEventType() != EventType.SUSPICIOUS_LOGIN || event.getUsername() == null) {
            return Optional.empty();
        }
        ConfigReader cfg = ConfigReader.of(rule.getConfig(), objectMapper);
        int oddStart = cfg.getInt("oddHourStart", 0);
        int oddEnd = cfg.getInt("oddHourEnd", 5);

        Long orgId = event.getOrg().getId();
        List<Long> matched = new ArrayList<>();
        matched.add(event.getId());

        boolean newCountry = false;
        if (event.getGeoCountry() != null) {
            Optional<SecurityEvent> prev = ctx.previousUserEvent(orgId, event.getUsername(), event.getId());
            if (prev.isPresent() && !event.getGeoCountry().equalsIgnoreCase(prev.get().getGeoCountry())) {
                newCountry = true;
                matched.add(prev.get().getId());
            }
        }

        int hour = event.getEventTimestamp().atZone(ZoneOffset.UTC).getHour();
        boolean oddHour = hour >= oddStart && hour < oddEnd;

        if (!newCountry && !oddHour) {
            return Optional.empty();
        }
        String why = newCountry ? ("new country " + event.getGeoCountry()) : ("odd hour " + hour + ":00 UTC");
        String message = "Suspicious login for %s (%s)".formatted(event.getUsername(), why);
        return Optional.of(new AlertDraft(
                rule.getSeverity(), rule.getMitreTechnique(), message, "user:" + event.getUsername(), matched));
    }
}
