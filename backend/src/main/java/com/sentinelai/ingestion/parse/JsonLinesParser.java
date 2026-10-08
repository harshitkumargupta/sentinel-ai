package com.sentinelai.ingestion.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JSON lines: one JSON object per line, in the security-event schema (generic normalizer). A
 * {@code time}/{@code timestamp} field is accepted as an alias of {@code eventTimestamp}.
 */
@Component
@RequiredArgsConstructor
public class JsonLinesParser implements LogLineParser {

    private final ObjectMapper objectMapper;

    @Override
    public LogFormat format() {
        return LogFormat.JSON_LINES;
    }

    @Override
    public Optional<ParsedRecord> parse(String line, Context ctx) {
        JsonNode node;
        try {
            node = objectMapper.readTree(line);
        } catch (Exception e) {
            throw new LineParseException("invalid JSON");
        }
        if (node == null || !node.isObject()) {
            throw new LineParseException("JSON line must be an object");
        }
        ObjectNode p = (ObjectNode) node;
        for (String alias : new String[]{"timestamp", "time", "@timestamp"}) {
            if (!p.has("eventTimestamp") && p.hasNonNull(alias)) {
                p.set("eventTimestamp", p.get(alias));
            }
        }
        p.put("rawMessage", line);
        return Optional.of(new ParsedRecord("generic", p));
    }
}
