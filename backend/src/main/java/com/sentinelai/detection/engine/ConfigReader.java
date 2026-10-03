package com.sentinelai.detection.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Safe accessor over a detection rule's {@code config} JSON, with defaults. Never throws on bad
 * input — a malformed config simply yields the provided defaults.
 */
public final class ConfigReader {

    private final JsonNode node;

    private ConfigReader(JsonNode node) {
        this.node = node;
    }

    public static ConfigReader of(String json, ObjectMapper mapper) {
        try {
            if (json == null || json.isBlank()) {
                return new ConfigReader(mapper.createObjectNode());
            }
            return new ConfigReader(mapper.readTree(json));
        } catch (Exception e) {
            return new ConfigReader(mapper.createObjectNode());
        }
    }

    public int getInt(String key, int def) {
        JsonNode n = node.get(key);
        return n == null || !n.canConvertToInt() ? def : n.asInt();
    }

    public String getString(String key, String def) {
        JsonNode n = node.get(key);
        return n == null || n.isNull() ? def : n.asText();
    }
}
