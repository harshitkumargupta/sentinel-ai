package com.sentinelai.adminrisk.factor;

import com.sentinelai.adminrisk.AdminActionContext;
import com.sentinelai.adminrisk.AdminActionRiskFactor;
import com.sentinelai.adminrisk.AdminRiskProperties;
import com.sentinelai.risk.FactorResult;
import org.springframework.stereotype.Component;

/** Acting far more than peers (possible compromised/abusive admin). */
@Component
public class PeerDeviationFactor implements AdminActionRiskFactor {
    @Override
    public FactorResult score(AdminActionContext ctx, AdminRiskProperties props) {
        double peer = ctx.peerAverageActions();
        if (peer > 0 && ctx.recentActionCount() > peer * props.getPeerDeviationMultiple()) {
            return new FactorResult("peer_deviation", props.getPeerDeviationPoints(),
                    "Acting %.1fx the peer average".formatted(ctx.recentActionCount() / peer));
        }
        return FactorResult.none("peer_deviation", "In line with peers");
    }
}
