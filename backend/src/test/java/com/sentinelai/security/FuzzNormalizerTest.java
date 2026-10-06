package com.sentinelai.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinelai.ingestion.normalize.AuthLogNormalizer;
import com.sentinelai.ingestion.normalize.EventNormalizer;
import com.sentinelai.ingestion.normalize.GenericJsonNormalizer;
import com.sentinelai.ingestion.normalize.NormalizedEvent;
import com.sentinelai.ingestion.normalize.WebAccessLogNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-style fuzz of the ingest normalizers: thousands of randomly-shaped, malformed JSON
 * payloads (wrong types, nulls, nested junk, huge/empty/odd values) must never make a normalizer
 * throw — it either maps or falls back to defaults, so a bad event can never 500 the API or crash a
 * Kafka consumer. Deterministic via a fixed seed (per the project's testing standard).
 */
class FuzzNormalizerTest {

    private static final long SEED = 42L;
    private static final int ITERATIONS = 3_000;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<EventNormalizer> normalizers = List.of(
            new GenericJsonNormalizer(), new AuthLogNormalizer(), new WebAccessLogNormalizer());

    private static final String[] KEYS = {
            "eventType", "severity", "sourceIp", "username", "userAgent", "resource",
            "assetCriticality", "geoCountry", "honeytoken", "eventTimestamp", "clientEventId",
            "status", "method", "path", "message", "ts", "ip", "user", "unexpected", "nested"
    };

    @Test
    void normalizersNeverThrowOnRandomMalformedInput() {
        Random rnd = new Random(SEED);
        for (int i = 0; i < ITERATIONS; i++) {
            JsonNode raw = randomJson(rnd, 0);
            for (EventNormalizer normalizer : normalizers) {
                NormalizedEvent out = normalizer.normalize(raw);
                // Must return a usable event; eventType/severity always have a safe default.
                assertThat(out).as("normalizer=%s input=%s", normalizer.sourceType(), raw).isNotNull();
                assertThat(out.getEventType()).isNotNull();
                assertThat(out.getSeverity()).isNotNull();
            }
        }
    }

    private JsonNode randomJson(Random rnd, int depth) {
        int choice = rnd.nextInt(depth > 3 ? 6 : 9);
        return switch (choice) {
            case 0 -> mapper.nullNode();
            case 1 -> mapper.getNodeFactory().textNode(randomString(rnd));
            case 2 -> mapper.getNodeFactory().numberNode(rnd.nextLong());
            case 3 -> mapper.getNodeFactory().booleanNode(rnd.nextBoolean());
            case 4 -> mapper.getNodeFactory().numberNode(rnd.nextDouble() * 1e9);
            case 5 -> mapper.getNodeFactory().textNode(""); // empty string edge
            case 6 -> randomArray(rnd, depth);
            default -> randomObject(rnd, depth);
        };
    }

    private ObjectNode randomObject(Random rnd, int depth) {
        ObjectNode node = mapper.createObjectNode();
        int fields = rnd.nextInt(6);
        for (int i = 0; i < fields; i++) {
            String key = KEYS[rnd.nextInt(KEYS.length)];
            node.set(key, randomJson(rnd, depth + 1));
        }
        return node;
    }

    private ArrayNode randomArray(Random rnd, int depth) {
        ArrayNode node = mapper.createArrayNode();
        int n = rnd.nextInt(4);
        for (int i = 0; i < n; i++) {
            node.add(randomJson(rnd, depth + 1));
        }
        return node;
    }

    private String randomString(Random rnd) {
        int len = rnd.nextInt(40);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            // Mix letters, digits, control/odd chars, and injection-ish tokens.
            int kind = rnd.nextInt(5);
            sb.append(switch (kind) {
                case 0 -> (char) ('a' + rnd.nextInt(26));
                case 1 -> (char) ('0' + rnd.nextInt(10));
                case 2 -> (char) rnd.nextInt(0x20);          // control chars incl. CR/LF
                case 3 -> "'\";<>${}".charAt(rnd.nextInt(8)); // injection-ish
                default -> (char) (0x80 + rnd.nextInt(0x100)); // extended/unicode
            });
        }
        return sb.toString();
    }
}
