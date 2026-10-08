package com.sentinelai.ingestion.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * nginx / Apache access log, "common" or "combined" format:
 * {@code 203.0.113.5 - alice [08/Oct/2026:10:15:01 +0000] "POST /login HTTP/1.1" 401 512 "-" "curl/8.0"}.
 * Feeds the existing {@code web} normalizer (status ≥ 400 → failure / API_ABUSE candidate).
 */
@Component
@RequiredArgsConstructor
public class AccessLogParser implements LogLineParser {

    private static final Pattern LINE = Pattern.compile(
            "^(\\S+) \\S+ (\\S+) \\[([^\\]]+)] \"(\\S+) (\\S+)(?: [^\"]*)?\" (\\d{3}) (\\d+|-)(?: \"([^\"]*)\" \"([^\"]*)\")?.*$");
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

    private final ObjectMapper objectMapper;

    @Override
    public LogFormat format() {
        return LogFormat.ACCESS_LOG;
    }

    @Override
    public Optional<ParsedRecord> parse(String line, Context ctx) {
        Matcher m = LINE.matcher(line);
        if (!m.matches()) {
            throw new LineParseException("not a common/combined access-log line");
        }
        ObjectNode p = objectMapper.createObjectNode();
        p.put("sourceIp", m.group(1));
        if (!"-".equals(m.group(2))) {
            p.put("username", m.group(2));
        }
        try {
            p.put("timestamp", OffsetDateTime.parse(m.group(3), TIME).toInstant().toString());
        } catch (DateTimeParseException e) {
            throw new LineParseException("bad access-log timestamp: " + m.group(3));
        }
        p.put("method", m.group(4));
        p.put("path", m.group(5));
        p.put("status", Integer.parseInt(m.group(6)));
        if (!"-".equals(m.group(7))) {
            p.put("bytes", Long.parseLong(m.group(7)));
        }
        if (m.group(9) != null && !"-".equals(m.group(9))) {
            p.put("userAgent", m.group(9));
        }
        p.put("rawMessage", line);
        return Optional.of(new ParsedRecord("web", p));
    }
}
