package com.sentinelai.incident.dto;

import com.sentinelai.alert.dto.AlertResponse;
import com.sentinelai.event.dto.EventResponse;

import java.util.List;

public record EvidenceResponse(List<EventResponse> events, List<AlertResponse> alerts) {
}
