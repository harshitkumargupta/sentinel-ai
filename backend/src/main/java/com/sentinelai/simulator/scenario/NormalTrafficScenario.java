package com.sentinelai.simulator.scenario;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Benign background traffic that must not trigger any rule (drives precision). */
@Component
public class NormalTrafficScenario implements Scenario {

    @Override
    public String id() {
        return "normal";
    }

    @Override
    public List<GeneratedEvent> generate(SimContext ctx) {
        List<GeneratedEvent> events = new ArrayList<>();
        int n = ctx.intensity() * 5;
        for (int i = 0; i < n; i++) {
            Instant ts = SimContext.BASE.plusSeconds(i * 7L);
            events.add(event(EventType.OTHER, Severity.LOW, ts, false, null,
                    map("username", "user" + (i % 20), "sourceIp", "203.0.113." + (i % 200 + 1),
                            "resource", "/api/data", "geoCountry", "US")));
        }
        // A few isolated failed logins (distinct users and IPs) — below every threshold.
        for (int j = 0; j < 3; j++) {
            Instant ts = SimContext.BASE.plusSeconds(1000 + j);
            events.add(event(EventType.FAILED_LOGIN, Severity.LOW, ts, false, null,
                    map("username", "normfail" + j, "sourceIp", "192.0.2." + (j + 1), "geoCountry", "DE")));
        }
        return events;
    }
}
