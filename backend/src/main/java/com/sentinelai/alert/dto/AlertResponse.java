package com.sentinelai.alert.dto;

import com.sentinelai.alert.domain.Alert;
import com.sentinelai.common.domain.Severity;

import java.time.Instant;

public record AlertResponse(
        Long id,
        Long ruleId,
        String ruleType,
        Integer ruleVersion,
        Severity severity,
        String mitreTechnique,
        String message,
        String entityKey,
        Long triggeringEventId,
        String matchedEventIds,
        String runId,
        Instant createdAt) {

    public static AlertResponse from(Alert a) {
        return new AlertResponse(a.getId(), a.getRuleId(), a.getRuleType(), a.getRuleVersion(),
                a.getSeverity(), a.getMitreTechnique(), a.getMessage(), a.getEntityKey(),
                a.getTriggeringEventId(), a.getMatchedEventIds(), a.getRunId(), a.getCreatedAt());
    }
}
