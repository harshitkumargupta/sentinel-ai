package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.engine.GroupBy;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

/** Phishing: a user clicked a link the mail/web gateway classified as malicious. config: {@code {threshold, windowSeconds, groupBy}}. */
@Component
public class PhishingRule extends EventCountRule {

    public PhishingRule(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String type() {
        return RuleTypes.PHISHING;
    }

    @Override
    protected EventType eventType() {
        return EventType.PHISHING_CLICK;
    }

    @Override
    protected String label() {
        return "Phishing link click";
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
