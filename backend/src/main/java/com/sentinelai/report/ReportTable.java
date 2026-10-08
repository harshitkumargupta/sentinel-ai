package com.sentinelai.report;

import java.time.Instant;
import java.util.List;

/** Format-independent report content: headline facts plus one table. */
public record ReportTable(String title, Instant from, Instant to, List<String> summary,
                          List<String> columns, List<List<String>> rows, boolean truncated) {
}
