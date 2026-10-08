package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.engine.ConfigReader;
import com.sentinelai.detection.engine.GroupBy;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import org.springframework.stereotype.Component;

/**
 * Data exfiltration: an outbound DATA_TRANSFER whose {@code bytesOut} (from the raw payload) is at
 * least {@code minBytes}. config: {@code {minBytes, threshold, windowSeconds, groupBy}}.
 */
@Component
public class DataExfiltrationRule extends EventCountRule {

    static final long DEFAULT_MIN_BYTES = 500L * 1024 * 1024;

    private final ObjectMapper objectMapper;

    public DataExfiltrationRule(ObjectMapper objectMapper) {
        super(objectMapper);
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return RuleTypes.DATA_EXFILTRATION;
    }

    @Override
    protected EventType eventType() {
        return EventType.DATA_TRANSFER;
    }

    @Override
    protected String label() {
        return "Large outbound transfer (exfiltration)";
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

    @Override
    protected boolean matches(SecurityEvent event, ConfigReader cfg) {
        long minBytes = parseLong(cfg.getString("minBytes", null), DEFAULT_MIN_BYTES);
        return bytesOut(event) >= minBytes;
    }

    private long bytesOut(SecurityEvent event) {
        if (event.getRawPayload() == null) {
            return 0;
        }
        try {
            JsonNode node = objectMapper.readTree(event.getRawPayload()).path("bytesOut");
            return node.canConvertToLong() ? node.asLong() : 0;
        } catch (Exception e) {
            return 0; // unparsable payload: not an exfiltration signal
        }
    }

    private static long parseLong(String raw, long def) {
        try {
            return raw == null ? def : Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
