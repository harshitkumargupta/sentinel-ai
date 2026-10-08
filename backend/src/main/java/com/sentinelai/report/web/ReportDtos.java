package com.sentinelai.report.web;

import com.sentinelai.report.ReportFormat;
import com.sentinelai.report.ReportSchedule;
import com.sentinelai.report.ReportType;
import com.sentinelai.report.ScheduleFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Reports API DTOs. */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record GenerateRequest(@NotNull ReportType type, @NotNull ReportFormat format,
                                  @NotNull Instant from, @NotNull Instant to) {
    }

    public record SaveScheduleRequest(@NotBlank @Size(max = 100) String name, @NotNull ReportType type,
                                      @NotNull ReportFormat format, @NotNull ScheduleFrequency frequency,
                                      @Min(1) @Max(7) Integer dayOfWeek, @NotNull @Min(0) @Max(23) Integer hourUtc,
                                      Boolean enabled) {
    }

    public record ScheduleView(Long id, String name, ReportType type, ReportFormat format, ScheduleFrequency frequency,
                               Integer dayOfWeek, int hourUtc, boolean enabled, Instant lastRunAt, Instant nextRunAt) {
        public static ScheduleView from(ReportSchedule s) {
            return new ScheduleView(s.getId(), s.getName(), s.getReportType(), s.getFormat(), s.getFrequency(),
                    s.getDayOfWeek(), s.getHourUtc(), s.isEnabled(), s.getLastRunAt(), s.getNextRunAt());
        }
    }
}
