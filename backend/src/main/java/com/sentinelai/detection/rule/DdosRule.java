package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.engine.GroupBy;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

/** DDoS: a flood of requests against one target service, from any number of IPs (grouped by the target's entity key). config: {@code {threshold, windowSeconds, groupBy}}. */
@Component
public class DdosRule extends EventCountRule {

    public DdosRule(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String type() {
        return RuleTypes.DDOS;
    }

    @Override
    protected EventType eventType() {
        return EventType.NETWORK_FLOOD;
    }

    @Override
    protected String label() {
        return "Traffic flood (DDoS)";
    }

    @Override
    protected int defaultThreshold() {
        return 150;
    }

    @Override
    protected int defaultWindowSeconds() {
        return 60;
    }

    @Override
    protected GroupBy defaultGroupBy() {
        return GroupBy.ENTITY_KEY;
    }
}
