package com.sentinelai.risk.factor;

import com.sentinelai.risk.FactorResult;
import com.sentinelai.risk.RiskContext;
import com.sentinelai.risk.RiskFactor;
import org.springframework.stereotype.Component;

/** Higher asset criticality among the touched events raises risk. */
@Component
public class AssetCriticalityFactor implements RiskFactor {

    @Override
    public FactorResult score(RiskContext ctx) {
        int maxCrit = ctx.events().stream()
                .map(e -> e.getAssetCriticality() == null ? 0 : (int) e.getAssetCriticality())
                .max(Integer::compareTo).orElse(0);
        int points = Math.min(maxCrit * ctx.props().getAssetCriticalityPerLevel(),
                ctx.props().getAssetCriticalityCap());
        // Name the most critical inventoried asset(s), so the breakdown says *which* asset raised risk.
        String assets = ctx.events().stream()
                .filter(e -> e.getAsset() != null && e.getAssetCriticality() != null && e.getAssetCriticality() == maxCrit)
                .map(e -> e.getAsset().label() + " (" + e.getAsset().getCriticality() + ")")
                .distinct().limit(3).collect(java.util.stream.Collectors.joining(", "));
        return new FactorResult("asset_criticality", points, maxCrit == 0 ? "No critical assets involved"
                : "Max asset criticality " + maxCrit + (assets.isEmpty() ? "" : ": " + assets));
    }
}
