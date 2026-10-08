package com.sentinelai.event.dto;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventOutcome;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;

import java.time.Instant;

public record EventResponse(
        Long id,
        Long orgId,
        EventType eventType,
        Severity severity,
        String sourceIp,
        String username,
        String userAgent,
        String resource,
        Byte assetCriticality,
        String rawPayload,
        String geoCountry,
        String geoCity,
        boolean honeytoken,
        String entityKey,
        String correlationKey,
        Instant eventTimestamp,
        Instant ingestedAt,
        EventOutcome outcome,
        Long sourceId) {

    public static EventResponse from(SecurityEvent e) {
        return new EventResponse(
                e.getId(), e.getOrg().getId(), e.getEventType(), e.getSeverity(), e.getSourceIp(),
                e.getUsername(), e.getUserAgent(), e.getResource(), e.getAssetCriticality(),
                e.getRawPayload(), e.getGeoCountry(), e.getGeoCity(), e.isHoneytoken(),
                e.getEntityKey(), e.getCorrelationKey(), e.getEventTimestamp(), e.getIngestedAt(),
                e.getOutcome(), e.getSite() == null ? null : e.getSite().getId());
    }
}
