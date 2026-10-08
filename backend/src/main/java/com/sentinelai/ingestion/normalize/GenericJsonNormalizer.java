package com.sentinelai.ingestion.normalize;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventOutcome;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

import static com.sentinelai.ingestion.normalize.NormalizerSupport.bool;
import static com.sentinelai.ingestion.normalize.NormalizerSupport.eventType;
import static com.sentinelai.ingestion.normalize.NormalizerSupport.severity;
import static com.sentinelai.ingestion.normalize.NormalizerSupport.text;
import static com.sentinelai.ingestion.normalize.NormalizerSupport.timestamp;

/** Maps a payload whose keys already match the security-event schema. */
@Component
public class GenericJsonNormalizer implements EventNormalizer {

    @Override
    public String sourceType() {
        return "generic";
    }

    @Override
    public NormalizedEvent normalize(JsonNode raw) {
        Byte criticality = raw.has("assetCriticality") && raw.get("assetCriticality").canConvertToInt()
                ? (byte) raw.get("assetCriticality").asInt() : null;
        return NormalizedEvent.builder()
                .eventType(eventType(raw, "eventType", EventType.OTHER))
                .severity(severity(raw, "severity", Severity.LOW))
                .outcome(EventOutcome.parse(text(raw, "outcome")))
                .sourceIp(text(raw, "sourceIp"))
                .username(text(raw, "username"))
                .userAgent(text(raw, "userAgent"))
                .resource(text(raw, "resource"))
                .assetCriticality(criticality)
                .geoCountry(text(raw, "geoCountry"))
                .geoCity(text(raw, "geoCity"))
                .honeytoken(bool(raw, "honeytoken"))
                .entityKey(text(raw, "entityKey"))
                .correlationKey(text(raw, "correlationKey"))
                .eventTimestamp(timestamp(raw, "eventTimestamp"))
                .clientEventId(text(raw, "clientEventId"))
                .rawPayload(raw.toString())
                .build();
    }
}
