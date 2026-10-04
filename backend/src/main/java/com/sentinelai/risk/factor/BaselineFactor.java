package com.sentinelai.risk.factor;

import com.sentinelai.baseline.BaselineProperties;
import com.sentinelai.baseline.BehavioralBaselineService;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.risk.FactorResult;
import com.sentinelai.risk.RiskContext;
import com.sentinelai.risk.RiskFactor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

/** Adds risk proportional to how far the incident's logins deviate from the user's baseline. */
@Component
@RequiredArgsConstructor
public class BaselineFactor implements RiskFactor {

    private static final int CAP = 20;
    private final BehavioralBaselineService baselines;
    private final BaselineProperties props;

    @Override
    public FactorResult score(RiskContext ctx) {
        double maxZ = 0.0;
        for (var e : ctx.events()) {
            if (e.getEventType() == EventType.SUSPICIOUS_LOGIN && e.getUsername() != null) {
                int hour = e.getEventTimestamp().atZone(ZoneOffset.UTC).getHour();
                double z = baselines.zScore("user:" + e.getUsername(), "login_hour", hour)
                        .map(Math::abs).orElse(0.0);
                maxZ = Math.max(maxZ, z);
            }
        }
        if (maxZ <= props.getZThreshold()) {
            return FactorResult.none("baseline", "Within behavioral baseline");
        }
        int points = (int) Math.min(CAP, Math.round(maxZ * 4));
        return new FactorResult("baseline", points, "Behavioral deviation z=%.1f".formatted(maxZ));
    }
}
