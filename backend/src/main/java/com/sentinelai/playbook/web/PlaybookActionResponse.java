package com.sentinelai.playbook.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.playbook.domain.PlaybookAction;

import java.time.Instant;

/** API view of a playbook action (never exposes the entity). */
public record PlaybookActionResponse(
        Long id,
        Long incidentId,
        String actionType,
        String target,
        String reason,
        String status,
        String riskLevel,
        boolean destructive,
        String proposedBy,
        String approvedBy,
        JsonNode dryRun,
        JsonNode beforeState,
        JsonNode afterState,
        String failureReason,
        Long analysisId,
        Instant expiresAt,
        Instant executedAt,
        Instant rolledBackAt,
        Instant createdAt) {

    public static PlaybookActionResponse from(PlaybookAction a, ObjectMapper mapper, boolean destructive) {
        return new PlaybookActionResponse(
                a.getId(),
                a.getIncident().getId(),
                a.getActionType(),
                a.getTargetRef(),
                a.getReason(),
                a.getStatus() == null ? null : a.getStatus().name(),
                a.getRiskLevel() == null ? null : a.getRiskLevel().name(),
                destructive,
                a.getProposedBy() == null ? null : a.getProposedBy().getUsername(),
                a.getApprovedBy() == null ? null : a.getApprovedBy().getUsername(),
                json(mapper, a.getDryRunResult()),
                json(mapper, a.getBeforeState()),
                json(mapper, a.getAfterState()),
                a.getFailureReason(),
                a.getAnalysisId(),
                a.getExpiresAt(),
                a.getExecutedAt(),
                a.getRolledBackAt(),
                a.getCreatedAt());
    }

    private static JsonNode json(ObjectMapper mapper, String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
