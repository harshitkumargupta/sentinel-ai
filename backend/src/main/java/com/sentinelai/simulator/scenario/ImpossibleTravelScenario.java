package com.sentinelai.simulator.scenario;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

import java.util.List;

/** Two logins for one user from different countries, too close together to be real travel. */
@Component
public class ImpossibleTravelScenario implements Scenario {

    @Override
    public String id() {
        return "impossible_travel";
    }

    @Override
    public List<GeneratedEvent> generate(SimContext ctx) {
        String user = "traveler";
        GeneratedEvent first = event(EventType.SUSPICIOUS_LOGIN, Severity.LOW, SimContext.BASE, false, null,
                map("username", user, "sourceIp", "203.0.113.11", "geoCountry", "US"));
        GeneratedEvent second = event(EventType.SUSPICIOUS_LOGIN, Severity.HIGH,
                SimContext.BASE.plusSeconds(1800), true, "IMPOSSIBLE_TRAVEL",
                map("username", user, "sourceIp", "198.51.100.22", "geoCountry", "RU"));
        return List.of(first, second);
    }
}
