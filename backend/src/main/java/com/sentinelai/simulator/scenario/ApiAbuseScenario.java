package com.sentinelai.simulator.scenario;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A burst of API error responses from one IP (high request rate). */
@Component
public class ApiAbuseScenario implements Scenario {

    @Override
    public String id() {
        return "api_abuse";
    }

    @Override
    public List<GeneratedEvent> generate(SimContext ctx) {
        List<GeneratedEvent> events = new ArrayList<>();
        String ip = "45.33.0.5";
        for (int i = 0; i < 120; i++) {
            Instant ts = SimContext.BASE.plusMillis(i * 400L);
            events.add(event(EventType.API_ABUSE, Severity.LOW, ts, true, "HIGH_FREQUENCY_API",
                    map("sourceIp", ip, "resource", "/api/search", "username", "api_client")));
        }
        return events;
    }
}
