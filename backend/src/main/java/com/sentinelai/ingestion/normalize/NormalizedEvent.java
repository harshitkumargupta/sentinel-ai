package com.sentinelai.ingestion.normalize;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * A raw input mapped onto the common security-event schema by an {@link EventNormalizer}, before
 * server-side enrichment/validation and persistence.
 */
@Data
@Builder
public class NormalizedEvent {
    private EventType eventType;
    private Severity severity;
    private String sourceIp;
    private String username;
    private String userAgent;
    private String resource;
    private Byte assetCriticality;
    private String rawPayload;
    private String geoCountry;
    private String geoCity;
    private boolean honeytoken;
    private String entityKey;
    private String correlationKey;
    private Instant eventTimestamp;
    private String clientEventId;
}
