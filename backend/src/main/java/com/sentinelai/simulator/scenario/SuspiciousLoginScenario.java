package com.sentinelai.simulator.scenario;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

import java.util.List;

/** A normal login, then an odd-hour login for the same user from the same country. */
@Component
public class SuspiciousLoginScenario implements Scenario {

    @Override
    public String id() {
        return "suspicious_login";
    }

    @Override
    public List<GeneratedEvent> generate(SimContext ctx) {
        String user = "sus_user";
        GeneratedEvent benign = event(EventType.SUSPICIOUS_LOGIN, Severity.LOW, SimContext.BASE, false, null,
                map("username", user, "sourceIp", "203.0.113.90", "geoCountry", "US"));
        GeneratedEvent oddHour = event(EventType.SUSPICIOUS_LOGIN, Severity.MEDIUM, SimContext.ODD_HOUR, true,
                "SUSPICIOUS_LOGIN",
                map("username", user, "sourceIp", "203.0.113.90", "geoCountry", "US"));
        return List.of(benign, oddHour);
    }
}
