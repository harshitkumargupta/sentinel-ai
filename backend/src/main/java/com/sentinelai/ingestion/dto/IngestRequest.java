package com.sentinelai.ingestion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/** A single raw event to ingest. {@code sourceType} picks the normalizer (default "generic"). */
public record IngestRequest(
        String sourceType,
        String clientEventId,
        @NotNull JsonNode payload) {
}
