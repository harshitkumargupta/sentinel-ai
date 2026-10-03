package com.sentinelai.adminrisk;

import com.sentinelai.risk.FactorResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/** Deterministically composes the admin-risk factors into a score + band. */
@Service
@RequiredArgsConstructor
public class AdminRiskService {

    private final List<AdminActionRiskFactor> factors;
    private final AdminRiskProperties properties;

    public AdminRiskResult assess(AdminActionContext ctx) {
        List<FactorResult> breakdown = factors.stream()
                .map(f -> f.score(ctx, properties))
                .sorted(Comparator.comparing(FactorResult::name))
                .toList();
        int raw = breakdown.stream().mapToInt(FactorResult::points).sum();
        int score = Math.min(raw, properties.getCap());
        return new AdminRiskResult(score, properties.bandFor(score), breakdown);
    }
}
