package com.sentinelai.ingestion.normalize;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventOutcome;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

import static com.sentinelai.ingestion.normalize.NormalizerSupport.text;
import static com.sentinelai.ingestion.normalize.NormalizerSupport.timestamp;

/**
 * Maps a web access-log line: {@code {sourceIp, method, path, status, userAgent, username, timestamp}}.
 * Error responses (status ≥ 400) become API_ABUSE candidates for the high-frequency rule.
 */
@Component
public class WebAccessLogNormalizer implements EventNormalizer {

    @Override
    public String sourceType() {
        return "web";
    }

    @Override
    public NormalizedEvent normalize(JsonNode raw) {
        int status = raw.has("status") && raw.get("status").canConvertToInt() ? raw.get("status").asInt() : 200;
        EventType type = status >= 400 ? EventType.API_ABUSE : EventType.OTHER;
        Severity severity = status >= 500 ? Severity.MEDIUM : Severity.LOW;
        return NormalizedEvent.builder()
                .eventType(type)
                .severity(severity)
                .outcome(status >= 400 ? EventOutcome.FAILURE : EventOutcome.SUCCESS)
                .sourceIp(text(raw, "sourceIp"))
                .username(text(raw, "username"))
                .userAgent(text(raw, "userAgent"))
                .resource(text(raw, "path"))
                .eventTimestamp(timestamp(raw, "timestamp"))
                .clientEventId(text(raw, "clientEventId"))
                .rawPayload(raw.toString())
                .build();
    }
}
