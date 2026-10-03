package com.sentinelai.adminrisk.factor;

import com.sentinelai.adminrisk.AdminActionContext;
import com.sentinelai.adminrisk.AdminActionRiskFactor;
import com.sentinelai.adminrisk.AdminRiskProperties;
import com.sentinelai.risk.FactorResult;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

/** Acting outside the admin's typical hours. */
@Component
public class TimeFactor implements AdminActionRiskFactor {
    @Override
    public FactorResult score(AdminActionContext ctx, AdminRiskProperties props) {
        var hours = ctx.baselines().typicalHours();
        if (hours == null || hours.isEmpty()) {
            return FactorResult.none("time", "No hour baseline");
        }
        int hour = ctx.at().atZone(ZoneOffset.UTC).getHour();
        return hours.contains(hour)
                ? FactorResult.none("time", "Within typical hours")
                : new FactorResult("time", props.getOffHoursPoints(), "Off-hours action at " + hour + ":00 UTC");
    }
}
