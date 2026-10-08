package com.sentinelai.ingestion.normalize;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventOutcome;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

import static com.sentinelai.ingestion.normalize.NormalizerSupport.bool;
import static com.sentinelai.ingestion.normalize.NormalizerSupport.text;
import static com.sentinelai.ingestion.normalize.NormalizerSupport.timestamp;

/**
 * Maps an auth-log line: {@code {username, sourceIp, success, userAgent, geoCountry, host, timestamp}};
 * the host (when present) becomes the {@code host:<name>} entity key so endpoint actions can target it.
 * Failures become FAILED_LOGIN; successes become SUSPICIOUS_LOGIN candidates for geo/odd-hour rules.
 */
@Component
public class AuthLogNormalizer implements EventNormalizer {

    @Override
    public String sourceType() {
        return "auth";
    }

    @Override
    public NormalizedEvent normalize(JsonNode raw) {
        boolean success = bool(raw, "success");
        EventType type = success ? EventType.SUSPICIOUS_LOGIN : EventType.FAILED_LOGIN;
        return NormalizedEvent.builder()
                .eventType(type)
                .severity(Severity.LOW)
                .outcome(success ? EventOutcome.SUCCESS : EventOutcome.FAILURE)
                .sourceIp(text(raw, "sourceIp"))
                .username(text(raw, "username"))
                .userAgent(text(raw, "userAgent"))
                .resource("auth/login")
                .geoCountry(text(raw, "geoCountry"))
                .entityKey(text(raw, "host") == null ? null : "host:" + text(raw, "host"))
                .eventTimestamp(timestamp(raw, "timestamp"))
                .clientEventId(text(raw, "clientEventId"))
                .rawPayload(raw.toString())
                .build();
    }
}
