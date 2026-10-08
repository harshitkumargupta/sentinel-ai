package com.sentinelai.report;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Report limits and the scheduler switch. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.reports")
public class ReportProperties {

    /** Longest date range one report may cover. */
    @Min(1) @Max(3660)
    private int maxRangeDays = 366;

    /** Rows per report table (the summary still covers everything). */
    @Min(10) @Max(100_000)
    private int maxRows = 5000;

    /** Largest stored report file. */
    @Min(10_000)
    private int maxBytes = 5 * 1024 * 1024;

    /** Run due schedules automatically (off in tests, which drive the scheduler directly). */
    private boolean schedulerEnabled = true;
}
