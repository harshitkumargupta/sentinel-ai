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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * UBA: a sudden spike in one user's failed logins versus their own history. The user's failures in
 * the last {@code windowSeconds} must reach max({@code minCount}, mean + {@code zThreshold}·σ) of
 * their hourly failure counts over {@code lookbackDays} (σ floored at 1). Fires once, when the count
 * first crosses that line.
 */
@Component
@RequiredArgsConstructor
public class FailedLoginSpikeRule implements DetectionRuleEvaluator {

    private final ObjectMapper objectMapper;
    private final UserBaselineQueries baselines;

    @Override
    public String type() {
        return RuleTypes.UBA_FAILED_SPIKE;
    }

    @Override
    public Optional<AlertDraft> evaluate(SecurityEvent event, DetectionRule rule, RuleContext ctx) {
        if (event.getEventType() != EventType.FAILED_LOGIN || event.getUsername() == null) {
            return Optional.empty();
        }
        ConfigReader cfg = ConfigReader.of(rule.getConfig(), objectMapper);
        int window = cfg.getInt("windowSeconds", 3600);
        int lookback = cfg.getInt("lookbackDays", 14);
        int minCount = cfg.getInt("minCount", 5);
        double z = parseDouble(cfg.getString("zThreshold", "3"), 3.0);
        Instant at = event.getEventTimestamp();
        Long org = event.getOrg().getId();
        String user = event.getUsername();
        Instant windowStart = at.minusSeconds(window);
        List<Long> history = baselines.hourlyFailures(org, user, windowStart.minusSeconds(lookback * 86400L), windowStart);
        int hours = Math.max(1, lookback * 24);
        double mean = history.stream().mapToLong(Long::longValue).sum() / (double) hours;
        double var = history.stream().mapToDouble(c -> (c - mean) * (c - mean)).sum()
                + (hours - history.size()) * mean * mean; // empty hours count as zero failures
        double sd = Math.max(1.0, Math.sqrt(var / hours));
        double limit = Math.max(minCount, mean + z * sd);
        long now = baselines.failuresBetween(org, user, windowStart, at);
        if (now < limit || now - 1 >= limit) {
            return Optional.empty(); // below the line, or already alerted on this spike
        }
        String msg = "Failed-login spike for %s: %d in %ds vs baseline %.2f/h (σ %.2f, limit %.1f)"
                .formatted(user, now, window, mean, sd, limit);
        return Optional.of(new AlertDraft(rule.getSeverity(), rule.getMitreTechnique(), msg, "user:" + user,
                ctx.recentEventIds(org, EventType.FAILED_LOGIN, com.sentinelai.detection.engine.GroupBy.USERNAME,
                        user, window, 50)));
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s);
        } catch (RuntimeException e) {
            return def;
        }
    }
}
