package com.sentinelai.adminrisk;

import com.sentinelai.risk.FactorResult;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/** Deterministic, template-based explanation (placeholder for a future AI explainer). */
@Component
public class TemplateAdminRiskExplainer implements AdminRiskExplainer {

    @Override
    public String explain(AdminActionContext ctx, AdminRiskResult result, String decision) {
        String reasons = result.breakdown().stream()
                .filter(f -> f.points() > 0)
                .map(FactorResult::reason)
                .collect(Collectors.joining("; "));
        if (reasons.isEmpty()) {
            reasons = "no elevated risk signals";
        }
        return "%s '%s' by %s scored %d (%s): %s → %s.".formatted(
                ctx.action(), ctx.entityType() == null ? "" : ctx.entityType(),
                ctx.actorUsername(), result.score(), result.band(), reasons, decision);
    }
}
