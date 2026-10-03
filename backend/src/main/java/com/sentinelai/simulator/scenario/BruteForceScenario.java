package com.sentinelai.simulator.scenario;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Burst of failed logins against one account from one IP, then a success. */
@Component
public class BruteForceScenario implements Scenario {

    @Override
    public String id() {
        return "brute_force";
    }

    @Override
    public List<GeneratedEvent> generate(SimContext ctx) {
        List<GeneratedEvent> events = new ArrayList<>();
        String user = "bf_victim";
        String ip = "203.0.113.50";
        for (int i = 0; i < 12; i++) {
            Instant ts = SimContext.BASE.plusSeconds(i * 10L);
            events.add(event(EventType.FAILED_LOGIN, Severity.LOW, ts, true, "BRUTE_FORCE",
                    map("username", user, "sourceIp", ip, "geoCountry", "US")));
        }
        return events;
    }
}
