package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.engine.GroupBy;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

/** SQL injection: repeated WAF-flagged SQLi requests from one source IP. config: {@code {threshold, windowSeconds, groupBy}}. */
@Component
public class SqlInjectionRule extends EventCountRule {

    public SqlInjectionRule(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String type() {
        return RuleTypes.SQL_INJECTION;
    }

    @Override
    protected EventType eventType() {
        return EventType.SQL_INJECTION;
    }

    @Override
    protected String label() {
        return "SQL injection attempts";
    }

    @Override
    protected int defaultThreshold() {
        return 3;
    }

    @Override
    protected int defaultWindowSeconds() {
        return 300;
    }

    @Override
    protected GroupBy defaultGroupBy() {
        return GroupBy.SOURCE_IP;
    }
}
