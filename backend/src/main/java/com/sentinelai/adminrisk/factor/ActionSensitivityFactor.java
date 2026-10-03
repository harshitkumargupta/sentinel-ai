package com.sentinelai.adminrisk.factor;

import com.sentinelai.adminrisk.AdminActionContext;
import com.sentinelai.adminrisk.AdminActionRiskFactor;
import com.sentinelai.adminrisk.AdminRiskProperties;
import com.sentinelai.risk.FactorResult;
import org.springframework.stereotype.Component;

/** Destructive / sensitive actions carry inherent risk. */
@Component
public class ActionSensitivityFactor implements AdminActionRiskFactor {
    @Override
    public FactorResult score(AdminActionContext ctx, AdminRiskProperties props) {
        return props.getSensitiveActions().contains(ctx.action())
                ? new FactorResult("action_sensitivity", props.getSensitiveActionPoints(),
                        "Sensitive action " + ctx.action())
                : FactorResult.none("action_sensitivity", "Routine action");
    }
}
