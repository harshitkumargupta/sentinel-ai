package com.sentinelai.adminrisk.factor;

import com.sentinelai.adminrisk.AdminActionContext;
import com.sentinelai.adminrisk.AdminActionRiskFactor;
import com.sentinelai.adminrisk.AdminBaselines;
import com.sentinelai.adminrisk.AdminRiskProperties;
import com.sentinelai.risk.FactorResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** New country / IP / device relative to the admin's baseline. */
@Component
public class NewIpCountryDeviceFactor implements AdminActionRiskFactor {
    @Override
    public FactorResult score(AdminActionContext ctx, AdminRiskProperties props) {
        AdminBaselines b = ctx.baselines();
        int points = 0;
        List<String> reasons = new ArrayList<>();
        if (isNew(b.knownCountries(), ctx.country())) {
            points += props.getNewCountryPoints();
            reasons.add("new country " + ctx.country());
        }
        if (isNew(b.knownIps(), ctx.ipAddress())) {
            points += props.getNewIpPoints();
            reasons.add("new IP");
        }
        if (isNew(b.knownDevices(), ctx.device())) {
            points += props.getNewDevicePoints();
            reasons.add("new device");
        }
        return points == 0
                ? FactorResult.none("new_ip_country_device", "Known origin")
                : new FactorResult("new_ip_country_device", points, String.join(", ", reasons));
    }

    private boolean isNew(Set<String> baseline, String value) {
        return baseline != null && !baseline.isEmpty() && value != null && !baseline.contains(value);
    }
}
