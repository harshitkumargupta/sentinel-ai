package com.sentinelai.reference.web;

import com.sentinelai.reference.ReferenceSetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Reference-set API DTOs. */
public final class ReferenceSetDtos {

    private ReferenceSetDtos() {
    }

    public record CreateSetRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[\\p{L}\\p{N} ._()\\-]+$",
                    message = "letters, digits, spaces and . _ ( ) - only") String name,
            @NotNull ReferenceSetType elementType,
            @Size(max = 500) String description) {
    }

    public record AddItemsRequest(@NotNull @Size(max = 5000) List<@Size(max = 255) String> values,
                                  @Size(max = 255) String note) {
    }

    public record SetView(Long id, String name, ReferenceSetType elementType, String description, long size,
                          Instant updatedAt) {
    }

    public record ItemView(Long id, String value, String note, String addedBy, Instant createdAt) {
    }

    public record AddItemsResult(int added, int duplicates, List<String> invalid) {
    }
}
