package com.sentinelai.ingestion.normalize;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/** Null-safe field accessors shared by normalizers. */
final class NormalizerSupport {

    private NormalizerSupport() {
    }

    static String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    static EventType eventType(JsonNode node, String field, EventType def) {
        String v = text(node, field);
        if (v == null) {
            return def;
        }
        try {
            return EventType.valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    static Severity severity(JsonNode node, String field, Severity def) {
        String v = text(node, field);
        if (v == null) {
            return def;
        }
        try {
            return Severity.valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    static Instant timestamp(JsonNode node, String field) {
        String v = text(node, field);
        if (v == null) {
            return null;
        }
        try {
            return Instant.parse(v);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static boolean bool(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n != null && n.asBoolean(false);
    }
}
