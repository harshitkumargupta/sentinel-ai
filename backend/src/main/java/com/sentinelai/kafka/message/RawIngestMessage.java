package com.sentinelai.kafka.message;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Carried on {@code events.raw}: a raw, not-yet-persisted event payload from an external collector
 * or the burst simulator. The raw-ingest consumer normalizes and persists it (writing the outbox
 * row), so it converges on the same path as the REST ingest endpoint.
 */
public record RawIngestMessage(Long orgId, Long siteId, String sourceType, JsonNode payload,
                               String clientEventId) {
}
