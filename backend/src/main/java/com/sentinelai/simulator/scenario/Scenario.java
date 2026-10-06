package com.sentinelai.simulator.scenario;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces a deterministic, labeled batch of events for one attack/benign pattern. Add a scenario
 * by adding a bean implementing this interface.
 */
public interface Scenario {

    String id();

    List<GeneratedEvent> generate(SimContext ctx);

    // --- helpers for implementations ---

    default GeneratedEvent event(EventType type, Severity severity, Instant ts, boolean attack,
                                 String expectedRule, Map<String, Object> extra) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("eventType", type.name());
        p.put("severity", severity.name());
        p.put("eventTimestamp", ts.toString());
        if (extra != null) {
            p.putAll(extra);
        }
        return new GeneratedEvent(p, id(), attack, expectedRule);
    }

    default Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] != null) {
                m.put(String.valueOf(kv[i]), kv[i + 1]);
            }
        }
        return m;
    }
}
