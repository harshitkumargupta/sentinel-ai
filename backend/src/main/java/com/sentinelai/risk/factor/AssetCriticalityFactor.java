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
        return new FactorResult("asset_criticality", points, "Max asset criticality " + maxCrit);
    }
}
