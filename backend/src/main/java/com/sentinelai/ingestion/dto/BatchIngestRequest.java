package com.sentinelai.ingestion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchIngestRequest(@NotEmpty @Valid List<IngestRequest> events) {
}
