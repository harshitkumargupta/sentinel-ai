package com.sentinelai.ingestion.dto;

public record IngestResponse(Long eventId, boolean duplicate) {
}
