package com.sentinelai.ingestion.parse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Parses one line of a raw log format into a structured record. Implementations are stateless
 * Spring beans discovered by {@link #format()} — add a format by adding a bean. Per-batch state
 * (the reference time, a CSV header) travels in {@link Context}.
 */
public interface LogLineParser {

    /** @param now reference time for formats without a year (syslog); {@code header} for CSV, else empty */
    record Context(Instant now, List<String> header) {
    }

    LogFormat format();

    /** True when the first non-blank line is a header row, not data. */
    default boolean hasHeader() {
        return false;
    }

    /**
     * @return the record, or empty if the line is recognized but not security-relevant
     * @throws LineParseException if the line is malformed for this format
     */
    Optional<ParsedRecord> parse(String line, Context ctx);
}
