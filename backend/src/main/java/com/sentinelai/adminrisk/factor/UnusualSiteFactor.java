package com.sentinelai.adminrisk.factor;

import com.sentinelai.adminrisk.AdminActionContext;
import com.sentinelai.adminrisk.AdminActionRiskFactor;
import com.sentinelai.adminrisk.AdminRiskProperties;
import com.sentinelai.risk.FactorResult;
import org.springframework.stereotype.Component;

/** Operating on a site the admin does not usually touch. */
@Component
public class UnusualSiteFactor implements AdminActionRiskFactor {
    @Override
    public FactorResult score(AdminActionContext ctx, AdminRiskProperties props) {
        var known = ctx.baselines().knownSites();
        if (ctx.siteId() != null && known != null && !known.isEmpty() && !known.contains(ctx.siteId())) {
            return new FactorResult("unusual_site", props.getUnusualSitePoints(),
                    "Unusual site " + ctx.siteId());
        }
        return FactorResult.none("unusual_site", "Usual site");
    }
}
