package com.sentinelai.risk.factor;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.risk.FactorResult;
import com.sentinelai.risk.RiskContext;
import com.sentinelai.risk.RiskFactor;
import org.springframework.stereotype.Component;

/** Points for the highest alert severity in the incident. */
@Component
public class SeverityFactor implements RiskFactor {

    @Override
    public FactorResult score(RiskContext ctx) {
        Severity max = ctx.alerts().stream().map(a -> a.getSeverity())
                .max(java.util.Comparator.comparingInt(Enum::ordinal)).orElse(null);
        if (max == null) {
            return FactorResult.none("severity", "No alerts");
        }
        int points = ctx.props().getSeverityPoints().getOrDefault(max, 0);
        return new FactorResult("severity", points, "Highest alert severity is " + max);
    }
}
