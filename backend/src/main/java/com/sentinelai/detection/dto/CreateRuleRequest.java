package com.sentinelai.detection.dto;

import com.sentinelai.common.domain.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRuleRequest(
        @NotBlank @Size(max = 150) String name,
        String description,
        @NotBlank @Size(max = 50) String ruleType,
        String config,
        @NotNull Severity severity,
        @Size(max = 20) String mitreTechnique,
        Boolean enabled) {
}
