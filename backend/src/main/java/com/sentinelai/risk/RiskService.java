package com.sentinelai.risk;

import com.sentinelai.alert.domain.Alert;
import com.sentinelai.event.domain.SecurityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Composes all {@link RiskFactor}s into a deterministic incident risk score (capped) and severity.
 * Factors are evaluated and ordered by name so the breakdown is stable for identical input.
 */
@Service
@RequiredArgsConstructor
public class RiskService {

    private final List<RiskFactor> factors;
    private final RiskProperties properties;

    public RiskResult score(List<Alert> alerts, List<SecurityEvent> events) {
        RiskContext ctx = new RiskContext(alerts, events, properties);
        List<FactorResult> breakdown = factors.stream()
                .map(f -> f.score(ctx))
                .sorted(Comparator.comparing(FactorResult::name))
                .toList();
        int raw = breakdown.stream().mapToInt(FactorResult::points).sum();
        int score = Math.min(raw, properties.getCap());
        return new RiskResult(score, properties.severityFor(score), breakdown);
    }
}
