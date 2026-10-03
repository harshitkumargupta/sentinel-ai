package com.sentinelai.risk.factor;

import com.sentinelai.event.domain.EventType;
import com.sentinelai.risk.FactorResult;
import com.sentinelai.risk.RiskContext;
import com.sentinelai.risk.RiskFactor;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Anomalous user behavior signals: new country / impossible travel, odd hour, unusual resource. */
@Component
public class UserBehaviorFactor implements RiskFactor {

    @Override
    public FactorResult score(RiskContext ctx) {
        var props = ctx.props();
        int points = 0;
        List<String> reasons = new ArrayList<>();

        boolean newCountry = ctx.alerts().stream().anyMatch(a ->
                "SUSPICIOUS_LOGIN".equals(a.getRuleType()) || "IMPOSSIBLE_TRAVEL".equals(a.getRuleType()));
        if (newCountry) {
            points += props.getNewCountryPoints();
            reasons.add("new country / impossible travel");
        }

        boolean oddHour = ctx.events().stream().anyMatch(e -> {
            int h = e.getEventTimestamp().atZone(ZoneOffset.UTC).getHour();
            return h >= 0 && h < 5;
        });
        if (oddHour) {
            points += props.getOddHourPoints();
            reasons.add("odd-hour activity");
        }

        boolean unusualResource = ctx.events().stream().anyMatch(e -> e.getEventType() == EventType.ABNORMAL_ACCESS);
        if (unusualResource) {
            points += props.getFirstTimeResourcePoints();
            reasons.add("access to an unusual resource");
        }

        points = Math.min(points, props.getUserBehaviorCap());
        String reason = reasons.isEmpty() ? "No anomalous user behavior" : String.join(", ", reasons);
        return new FactorResult("user_behavior", points, reason);
    }
}
