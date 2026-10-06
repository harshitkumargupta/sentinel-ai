package com.sentinelai.risk.factor;

import com.sentinelai.risk.FactorResult;
import com.sentinelai.risk.RiskContext;
import com.sentinelai.risk.RiskFactor;
import org.springframework.stereotype.Component;

/** Any honeytoken hit is a near-certain breach signal — large fixed contribution. */
@Component
public class HoneytokenFactor implements RiskFactor {

    @Override
    public FactorResult score(RiskContext ctx) {
        boolean hit = ctx.alerts().stream().anyMatch(a -> "HONEYTOKEN".equals(a.getRuleType()))
                || ctx.events().stream().anyMatch(e -> e.isHoneytoken());
        return hit
                ? new FactorResult("honeytoken", ctx.props().getHoneytokenPoints(), "Honeytoken was accessed")
                : FactorResult.none("honeytoken", "No honeytoken activity");
    }
}
