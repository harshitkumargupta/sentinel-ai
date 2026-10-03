package com.sentinelai.simulator.scenario;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Many distinct accounts failing login from a single IP. */
@Component
public class CredentialStuffingScenario implements Scenario {

    @Override
    public String id() {
        return "credential_stuffing";
    }

    @Override
    public List<GeneratedEvent> generate(SimContext ctx) {
        List<GeneratedEvent> events = new ArrayList<>();
        String ip = "198.51.100.10";
        for (int i = 0; i < 8; i++) {
            Instant ts = SimContext.BASE.plusSeconds(i * 10L);
            events.add(event(EventType.FAILED_LOGIN, Severity.LOW, ts, true, "CREDENTIAL_STUFFING",
                    map("username", "cs_user" + i, "sourceIp", ip, "geoCountry", "RU")));
        }
        return events;
    }
}
