package com.sentinelai.risk.factor;

import com.sentinelai.risk.FactorResult;
import com.sentinelai.risk.RiskContext;
import com.sentinelai.risk.RiskFactor;
import org.springframework.stereotype.Component;

/** Repeated rule firings (more than one alert) raise risk, capped. */
@Component
public class RepetitionFactor implements RiskFactor {

    @Override
    public FactorResult score(RiskContext ctx) {
        int extra = Math.max(0, ctx.alerts().size() - 1);
        int points = Math.min(extra * ctx.props().getRepetitionPerExtraAlert(), ctx.props().getRepetitionCap());
        return new FactorResult("repetition", points, ctx.alerts().size() + " alerts in this incident");
    }
}
