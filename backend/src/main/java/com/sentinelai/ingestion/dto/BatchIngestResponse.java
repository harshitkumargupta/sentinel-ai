package com.sentinelai.ingestion.dto;

import java.util.List;

public record BatchIngestResponse(int accepted, int duplicates, List<Long> eventIds) {
}
