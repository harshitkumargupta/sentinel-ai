package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.engine.AlertDraft;
import com.sentinelai.detection.engine.ConfigReader;
import com.sentinelai.detection.engine.DetectionRuleEvaluator;
import com.sentinelai.detection.engine.RuleContext;
import com.sentinelai.event.domain.SecurityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * UBA: a login at an hour this user rarely logs in at. From the user's prior logins in
 * {@code lookbackDays}, the share within ±{@code toleranceHours} of this hour must be below
 * {@code maxSharePercent}; needs {@code minSamples} prior logins (cold-start users are skipped).
 */
@Component
@RequiredArgsConstructor
public class UnusualLoginHourRule implements DetectionRuleEvaluator {

    private final ObjectMapper objectMapper;
    private final UserBaselineQueries baselines;

    @Override
    public String type() {
        return RuleTypes.UBA_UNUSUAL_HOUR;
    }

    @Override
    public Optional<AlertDraft> evaluate(SecurityEvent event, DetectionRule rule, RuleContext ctx) {
        if (event.getUsername() == null || !UserBaselineQueries.LOGIN_TYPES.contains(event.getEventType().name())) {
            return Optional.empty();
        }
        ConfigReader cfg = ConfigReader.of(rule.getConfig(), objectMapper);
        int minSamples = cfg.getInt("minSamples", 10);
        int lookback = cfg.getInt("lookbackDays", 30);
        int tolerance = cfg.getInt("toleranceHours", 1);
        int maxShare = cfg.getInt("maxSharePercent", 5);
        Instant at = event.getEventTimestamp();
        Long org = event.getOrg().getId();
        Map<Integer, Long> hours = baselines.loginHours(org, event.getUsername(), at.minusSeconds(lookback * 86400L), at);
        long total = hours.values().stream().mapToLong(Long::longValue).sum();
        if (total < minSamples) {
            return Optional.empty();
        }
        int hour = at.atOffset(ZoneOffset.UTC).getHour();
        long near = 0;
        for (int d = -tolerance; d <= tolerance; d++) {
            near += hours.getOrDefault(Math.floorMod(hour + d, 24), 0L);
        }
        double share = 100.0 * near / total;
        if (share >= maxShare) {
            return Optional.empty();
        }
        String msg = "Unusual login hour for %s: %02d:00 UTC (%.0f%% of %d prior logins within ±%dh)"
                .formatted(event.getUsername(), hour, share, total, tolerance);
        return Optional.of(new AlertDraft(rule.getSeverity(), rule.getMitreTechnique(), msg,
                "user:" + event.getUsername(), List.of(event.getId())));
    }
}
