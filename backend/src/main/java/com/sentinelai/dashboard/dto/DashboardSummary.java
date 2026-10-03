package com.sentinelai.dashboard.dto;

import java.util.Map;

public record DashboardSummary(
        long eventsLast24h,
        Map<String, Long> eventsBySeverity,
        Map<String, Long> eventsByType,
        Map<String, Long> incidentsByStatus,
        Map<String, Long> incidentsBySeverity) {
}
