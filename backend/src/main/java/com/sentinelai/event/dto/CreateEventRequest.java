package com.sentinelai.event.dto;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateEventRequest(
        @NotNull EventType eventType,
        @NotNull Severity severity,
        @Size(max = 45) String sourceIp,
        @Size(max = 100) String username,
        @Size(max = 512) String userAgent,
        @Size(max = 255) String resource,
        Byte assetCriticality,
        String rawPayload,
        @Size(max = 2) String geoCountry,
        @Size(max = 100) String geoCity,
        Boolean honeytoken,
        @Size(max = 255) String entityKey,
        @Size(max = 255) String correlationKey,
        Instant eventTimestamp) {
}
