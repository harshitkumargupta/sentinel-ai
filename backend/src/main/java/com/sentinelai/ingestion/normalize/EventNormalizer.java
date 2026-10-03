package com.sentinelai.ingestion.normalize;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Maps a raw input payload of a given {@link #sourceType()} onto the common security-event schema.
 * Add support for a new log format by adding a bean implementing this interface — no ingestion edits.
 */
public interface EventNormalizer {

    /** The {@code sourceType} this normalizer handles (e.g. "generic", "auth", "web"). */
    String sourceType();

    NormalizedEvent normalize(JsonNode raw);
}
