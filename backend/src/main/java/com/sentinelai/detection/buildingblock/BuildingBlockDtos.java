package com.sentinelai.detection.buildingblock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Building-block API DTOs. */
public final class BuildingBlockDtos {

    private BuildingBlockDtos() {
    }

    public record ConditionDto(@NotBlank String field, @NotNull ConditionOperator op,
                               @NotNull @Size(max = 200) List<@NotBlank @Size(max = 255) String> values) {
    }

    public record SaveBuildingBlockRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[\\p{L}\\p{N} ._()\\-]+$",
                    message = "letters, digits, spaces and . _ ( ) - only") String name,
            @Size(max = 500) String description,
            @NotEmpty @Size(max = 20) List<@Valid ConditionDto> conditions) {
    }

    public record BuildingBlockView(Long id, String name, String description, List<Condition> conditions,
                                    List<String> usedByRules, Instant updatedAt) {
    }
}
