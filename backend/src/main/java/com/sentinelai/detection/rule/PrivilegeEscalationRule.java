package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.engine.GroupBy;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

/** Privilege escalation: an account gained privileges it should not have. config: {@code {threshold, windowSeconds, groupBy}}. */
@Component
public class PrivilegeEscalationRule extends EventCountRule {

    public PrivilegeEscalationRule(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String type() {
        return RuleTypes.PRIVILEGE_ESCALATION;
    }

    @Override
    protected EventType eventType() {
        return EventType.PRIVILEGE_ESCALATION;
    }

    @Override
    protected String label() {
        return "Privilege escalation";
    }

    @Override
    protected int defaultThreshold() {
        return 1;
    }

    @Override
    protected int defaultWindowSeconds() {
        return 3600;
    }

    @Override
    protected GroupBy defaultGroupBy() {
        return GroupBy.USERNAME;
    }
}
