package com.sentinelai.ingestion.parse;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * One parsed line: a structured payload for an existing {@code EventNormalizer} ({@code sourceType}
 * selects it: {@code web}, {@code auth} or {@code generic}). The payload always carries the original
 * line as {@code rawMessage}.
 */
public record ParsedRecord(String sourceType, ObjectNode payload) {
}
