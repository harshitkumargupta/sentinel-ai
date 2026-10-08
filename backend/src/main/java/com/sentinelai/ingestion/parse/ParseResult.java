package com.sentinelai.ingestion.parse;

import java.util.List;

/**
 * Outcome of parsing a batch of lines: the records, how many lines were skipped as irrelevant
 * (recognized but not security-relevant, e.g. a cron session line), and how many were malformed,
 * with a few sample errors for the UI.
 */
public record ParseResult(List<ParsedRecord> records, int skipped, int errors, List<String> errorSamples) {
}
