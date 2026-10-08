package com.sentinelai.logsource.web;

import com.sentinelai.site.domain.LogSourceType;
import com.sentinelai.site.dto.SiteDtos.ApiKeyResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Log Sources API DTOs (entities never cross the boundary). */
public final class LogSourceDtos {

    private LogSourceDtos() {
    }

    public enum Health { RECEIVING, IDLE, NEVER, DISABLED }

    public record CreateLogSourceRequest(
            @NotBlank @Size(max = 150) @Pattern(regexp = "^[\\p{L}\\p{N} ._()\\-]+$",
                    message = "letters, digits, spaces and . _ ( ) - only") String name,
            @NotNull LogSourceType type,
            @Size(max = 500) String description) {
    }

    public record SetEnabledRequest(@NotNull Boolean enabled) {
    }

    public record LogSourceView(Long id, String name, LogSourceType type, String description, boolean enabled,
                                Health health, Instant lastEventAt, double eventsPerSecond, long totalEvents,
                                long parseErrors, long activeKeys, Instant createdAt) {
    }

    /** Returned once on create — the raw key is never stored or shown again. */
    public record CreatedLogSource(LogSourceView source, ApiKeyResponse apiKey) {
    }
}
