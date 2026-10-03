package com.sentinelai.incident.dto;

import com.sentinelai.event.dto.EventResponse;

import java.util.List;

public record IncidentDetailResponse(
        IncidentResponse incident,
        List<EventResponse> events) {
}
