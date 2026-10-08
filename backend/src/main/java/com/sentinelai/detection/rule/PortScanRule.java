package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.engine.GroupBy;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

/** Port scan: many PORT_SCAN probes (one per port) from one source IP. config: {@code {threshold, windowSeconds, groupBy}}. */
@Component
public class PortScanRule extends EventCountRule {

    public PortScanRule(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String type() {
        return RuleTypes.PORT_SCAN;
    }

    @Override
    protected EventType eventType() {
        return EventType.PORT_SCAN;
    }

    @Override
    protected String label() {
        return "Port scan";
    }

    @Override
    protected int defaultThreshold() {
        return 20;
    }

    @Override
    protected int defaultWindowSeconds() {
        return 120;
    }

    @Override
    protected GroupBy defaultGroupBy() {
        return GroupBy.SOURCE_IP;
    }
}
