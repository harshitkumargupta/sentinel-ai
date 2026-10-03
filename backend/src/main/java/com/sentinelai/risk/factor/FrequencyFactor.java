package com.sentinelai.risk.factor;

import com.sentinelai.risk.FactorResult;
import com.sentinelai.risk.RiskContext;
import com.sentinelai.risk.RiskFactor;
import org.springframework.stereotype.Component;

/** More contributing events → higher risk, capped. */
@Component
public class FrequencyFactor implements RiskFactor {

    @Override
    public FactorResult score(RiskContext ctx) {
        int events = ctx.events().size();
        int points = Math.min(events * ctx.props().getFrequencyPerEvent(), ctx.props().getFrequencyCap());
        return new FactorResult("frequency", points, events + " contributing events");
    }
}
