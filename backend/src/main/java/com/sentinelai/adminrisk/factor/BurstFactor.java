package com.sentinelai.adminrisk.factor;

import com.sentinelai.adminrisk.AdminActionContext;
import com.sentinelai.adminrisk.AdminActionRiskFactor;
import com.sentinelai.adminrisk.AdminRiskProperties;
import com.sentinelai.risk.FactorResult;
import org.springframework.stereotype.Component;

/** A burst of admin actions in a short window (e.g. a mass-disable). */
@Component
public class BurstFactor implements AdminActionRiskFactor {
    @Override
    public FactorResult score(AdminActionContext ctx, AdminRiskProperties props) {
        return ctx.recentActionCount() >= props.getBurstThreshold()
                ? new FactorResult("burst", props.getBurstPoints(),
                        ctx.recentActionCount() + " admin actions in " + props.getBurstWindowSeconds() + "s")
                : FactorResult.none("burst", "Normal action rate");
    }
}
