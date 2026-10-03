package com.sentinelai.simulator.scenario;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Access to a decoy credential. The value matches the dev-seeded honeytoken, so the honeytoken
 * rule hashes it to a hit.
 */
@Component
public class HoneytokenScenario implements Scenario {

    /** Must match a seeded honeytoken's raw value (see DevDataSeeder). */
    public static final String DECOY_VALUE = "AKIA-DECOY-EXAMPLE-0001";

    @Override
    public String id() {
        return "honeytoken";
    }

    @Override
    public List<GeneratedEvent> generate(SimContext ctx) {
        return List.of(event(EventType.HONEYTOKEN_ACCESS, Severity.HIGH, SimContext.BASE, true, "HONEYTOKEN",
                map("username", "attacker", "sourceIp", "185.220.1.1", "geoCountry", "NL",
                        "honeytoken", true, "value", DECOY_VALUE)));
    }
}
