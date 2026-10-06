package com.sentinelai.adminrisk;

import com.sentinelai.risk.FactorResult;

/**
 * A contributor to an admin action's risk score. Add one by adding a bean — {@code AdminRiskService}
 * composes them deterministically. Weights/thresholds come from {@link AdminRiskProperties}.
 */
public interface AdminActionRiskFactor {

    FactorResult score(AdminActionContext ctx, AdminRiskProperties props);
}
