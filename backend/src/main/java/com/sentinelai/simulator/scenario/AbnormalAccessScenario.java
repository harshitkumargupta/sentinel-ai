package com.sentinelai.simulator.scenario;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

import java.util.List;

/** A low-privilege user reaching an admin-only endpoint. */
@Component
public class AbnormalAccessScenario implements Scenario {

    @Override
    public String id() {
        return "abnormal_access";
    }

    @Override
    public List<GeneratedEvent> generate(SimContext ctx) {
        return List.of(event(EventType.ABNORMAL_ACCESS, Severity.HIGH, SimContext.BASE, true, "ABNORMAL_ACCESS",
                map("username", "viewer_bob", "sourceIp", "203.0.113.77",
                        "resource", "/api/admin/users", "geoCountry", "US")));
    }
}
