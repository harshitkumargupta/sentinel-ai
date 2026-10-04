package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.baseline.BehavioralBaselineService;
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
import java.util.List;
import java.util.Optional;

/**
 * Flags behavioral anomalies via z-score against a per-user baseline (default metric: login hour).
 * Cold-start users are skipped. config: {@code {metric, zThreshold}}.
 */
@Component
@RequiredArgsConstructor
public class BaselineDeviationRule implements DetectionRuleEvaluator {

    private final BehavioralBaselineService baselines;
    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return "BASELINE_DEVIATION";
    }

    @Override
    public Optional<AlertDraft> evaluate(SecurityEvent event, DetectionRule rule, RuleContext ctx) {
        if (event.getEventType() != EventType.SUSPICIOUS_LOGIN || event.getUsername() == null) {
            return Optional.empty();
        }
        ConfigReader cfg = ConfigReader.of(rule.getConfig(), objectMapper);
        String metric = cfg.getString("metric", "login_hour");
        double threshold = cfg.getInt("zThreshold", 3);

        String entityKey = "user:" + event.getUsername();
        int hour = event.getEventTimestamp().atZone(ZoneOffset.UTC).getHour();

        Optional<Double> z = baselines.zScore(entityKey, metric, hour);
        baselines.observe(entityKey, metric, hour); // online learning (after scoring)
        if (z.isEmpty() || Math.abs(z.get()) <= threshold) {
            return Optional.empty();
        }
        var stat = baselines.stat(entityKey, metric).orElse(null);
        String reason = "%s=%d for %s; baseline mean %.1f (±%.1f), z=%.1f".formatted(
                metric, hour, event.getUsername(),
                stat != null ? stat.mean() : 0.0, stat != null ? stat.std() : 0.0, z.get());
        return Optional.of(new AlertDraft(rule.getSeverity(), rule.getMitreTechnique(),
                reason, entityKey, List.of(event.getId())));
    }
}
