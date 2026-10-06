package com.sentinelai.detection.dto;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;

import java.time.Instant;

public record RuleResponse(
        Long id,
        Long orgId,
        String name,
        String description,
        String ruleType,
        String config,
        boolean enabled,
        Severity severity,
        String mitreTechnique,
        Integer version,
        Long createdById,
        Instant createdAt,
        Instant updatedAt) {

    public static RuleResponse from(DetectionRule r) {
        return new RuleResponse(
                r.getId(), r.getOrg().getId(), r.getName(), r.getDescription(), r.getRuleType(),
                r.getConfig(), r.isEnabled(), r.getSeverity(), r.getMitreTechnique(), r.getVersion(),
                r.getCreatedBy() != null ? r.getCreatedBy().getId() : null,
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
