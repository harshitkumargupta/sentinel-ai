package com.sentinelai.risk.factor;

import com.sentinelai.risk.FactorResult;
import com.sentinelai.risk.RiskContext;
import com.sentinelai.risk.RiskFactor;
import org.springframework.stereotype.Component;

/** Later kill-chain stages (by MITRE technique) weigh more; takes the max over the incident. */
@Component
public class MitreStageFactor implements RiskFactor {

    @Override
    public FactorResult score(RiskContext ctx) {
        var weights = ctx.props().getMitreStageWeights();
        int best = 0;
        String bestTech = null;
        for (var alert : ctx.alerts()) {
            String tech = alert.getMitreTechnique();
            if (tech != null) {
                int w = weights.getOrDefault(tech, 0);
                if (w > best) {
                    best = w;
                    bestTech = tech;
                }
            }
        }
        return bestTech == null
                ? FactorResult.none("mitre_stage", "No weighted MITRE techniques")
                : new FactorResult("mitre_stage", best, "Kill-chain weight for " + bestTech);
    }
}
