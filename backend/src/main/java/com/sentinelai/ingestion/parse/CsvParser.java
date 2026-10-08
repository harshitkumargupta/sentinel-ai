package com.sentinelai.ingestion.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * CSV with a header row (RFC 4180 quoting). Common column names are mapped onto the event schema —
 * e.g. {@code src}/{@code src_ip} → sourceIp, {@code user} → username, {@code action}/{@code result}
 * → outcome, {@code time} → eventTimestamp; unknown columns are kept in the raw payload.
 */
@Component
@RequiredArgsConstructor
public class CsvParser implements LogLineParser {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("time", "eventTimestamp"), Map.entry("timestamp", "eventTimestamp"),
            Map.entry("eventtimestamp", "eventTimestamp"), Map.entry("date", "eventTimestamp"),
            Map.entry("src", "sourceIp"), Map.entry("src_ip", "sourceIp"), Map.entry("source_ip", "sourceIp"),
            Map.entry("sourceip", "sourceIp"), Map.entry("ip", "sourceIp"), Map.entry("client_ip", "sourceIp"),
            Map.entry("user", "username"), Map.entry("username", "username"), Map.entry("account", "username"),
            Map.entry("event_type", "eventType"), Map.entry("eventtype", "eventType"), Map.entry("type", "eventType"),
            Map.entry("severity", "severity"), Map.entry("outcome", "outcome"), Map.entry("action", "outcome"),
            Map.entry("result", "outcome"), Map.entry("status", "outcome"),
            Map.entry("resource", "resource"), Map.entry("dst", "resource"), Map.entry("destination", "resource"),
            Map.entry("url", "resource"), Map.entry("path", "resource"),
            Map.entry("country", "geoCountry"), Map.entry("geo_country", "geoCountry"),
            Map.entry("user_agent", "userAgent"), Map.entry("useragent", "userAgent"));

    private final ObjectMapper objectMapper;

    @Override
    public LogFormat format() {
        return LogFormat.CSV;
    }

    @Override
    public boolean hasHeader() {
        return true;
    }

    @Override
    public Optional<ParsedRecord> parse(String line, Context ctx) {
        List<String> header = ctx.header();
        if (header == null || header.isEmpty()) {
            throw new LineParseException("CSV requires a header row");
        }
        List<String> cells = split(line);
        if (cells.size() != header.size()) {
            throw new LineParseException("expected " + header.size() + " columns, got " + cells.size());
        }
        ObjectNode p = objectMapper.createObjectNode();
        for (int i = 0; i < header.size(); i++) {
            String raw = header.get(i).trim();
            String key = ALIASES.getOrDefault(raw.toLowerCase(Locale.ROOT), raw);
            String value = cells.get(i).trim();
            if (!value.isEmpty() && !p.has(key)) {
                p.put(key, value);
            }
        }
        p.put("rawMessage", line);
        return Optional.of(new ParsedRecord("generic", p));
    }

    /** Split one CSV record (quotes, escaped quotes, commas inside quotes). */
    public static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (quoted) {
            throw new LineParseException("unterminated quote");
        }
        out.add(cur.toString());
        return out;
    }
}
