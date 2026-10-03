package com.sentinelai.detection.dto;

import com.sentinelai.common.domain.Severity;
import jakarta.validation.constraints.Size;

/** All fields optional; only non-null fields are applied. */
public record UpdateRuleRequest(
        @Size(max = 150) String name,
        String description,
        @Size(max = 50) String ruleType,
        String config,
        Severity severity,
        @Size(max = 20) String mitreTechnique,
        Boolean enabled) {
}
