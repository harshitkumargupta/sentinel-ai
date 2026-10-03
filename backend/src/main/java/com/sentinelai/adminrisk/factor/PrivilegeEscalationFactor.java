package com.sentinelai.adminrisk.factor;

import com.sentinelai.adminrisk.AdminActionContext;
import com.sentinelai.adminrisk.AdminActionRiskFactor;
import com.sentinelai.adminrisk.AdminRiskProperties;
import com.sentinelai.risk.FactorResult;
import org.springframework.stereotype.Component;

/** Granting elevated privileges (role change to ADMIN where it wasn't before). */
@Component
public class PrivilegeEscalationFactor implements AdminActionRiskFactor {
    @Override
    public FactorResult score(AdminActionContext ctx, AdminRiskProperties props) {
        String after = ctx.afterJson() == null ? "" : ctx.afterJson();
        String before = ctx.beforeJson() == null ? "" : ctx.beforeJson();
        boolean grantsAdmin = after.contains("\"role\":\"ADMIN\"") && !before.contains("\"role\":\"ADMIN\"");
        boolean escalateAction = "ROLE_CHANGE".equals(ctx.action()) && after.contains("ADMIN");
        return grantsAdmin || escalateAction
                ? new FactorResult("privilege_escalation", props.getPrivilegeEscalationPoints(),
                        "Grants ADMIN privilege")
                : FactorResult.none("privilege_escalation", "No privilege escalation");
    }
}
