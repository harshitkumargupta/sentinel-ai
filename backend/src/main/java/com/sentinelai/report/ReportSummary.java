package com.sentinelai.report;

import java.time.Instant;

/** List view of a generated report (never includes the file). */
public record ReportSummary(Long id, ReportType type, ReportFormat format, Instant from, Instant to,
                            GeneratedReport.Status status, int rows, int sizeBytes, String error,
                            Long scheduleId, Instant createdAt) {
}
