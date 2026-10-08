package com.sentinelai.incident.dto;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentFeedback;
import com.sentinelai.incident.domain.IncidentPriority;
import com.sentinelai.incident.domain.IncidentStatus;

import java.time.Instant;

public record IncidentResponse(
        Long id,
        Long orgId,
        String title,
        String description,
        IncidentStatus status,
        Severity severity,
        Integer riskScore,
        String riskBreakdown,
        IncidentFeedback feedback,
        Long assignedToId,
        Long createdById,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        IncidentPriority priority,
        String assignedTo,
        Instant closedAt) {

    public static IncidentResponse from(Incident i) {
        return new IncidentResponse(
                i.getId(), i.getOrg().getId(), i.getTitle(), i.getDescription(), i.getStatus(),
                i.getSeverity(), i.getRiskScore(), i.getRiskBreakdown(), i.getFeedback(),
                i.getAssignedTo() != null ? i.getAssignedTo().getId() : null,
                i.getCreatedBy() != null ? i.getCreatedBy().getId() : null,
                i.getCreatedAt(), i.getUpdatedAt(), i.getResolvedAt(),
                i.getPriority(),
                i.getAssignedTo() != null ? i.getAssignedTo().getUsername() : null,
                i.getClosedAt());
    }
}
