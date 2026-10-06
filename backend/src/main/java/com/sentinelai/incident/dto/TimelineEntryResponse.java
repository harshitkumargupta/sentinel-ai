package com.sentinelai.incident.dto;

import com.sentinelai.incident.domain.IncidentTimeline;

import java.time.Instant;

public record TimelineEntryResponse(
        Long id, String type, String actor, String detail, Instant createdAt) {

    public static TimelineEntryResponse from(IncidentTimeline t) {
        return new TimelineEntryResponse(t.getId(), t.getType(), t.getActor(), t.getDetail(), t.getCreatedAt());
    }
}
