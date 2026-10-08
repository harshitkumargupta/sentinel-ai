package com.sentinelai.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Checks for due report schedules once a minute (disable with sentinel.reports.scheduler-enabled=false). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sentinel.reports.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ReportScheduler {

    private final ReportScheduleService schedules;

    @Scheduled(fixedDelayString = "${sentinel.reports.scheduler-interval-ms:60000}", initialDelay = 30_000)
    public void tick() {
        try {
            schedules.runDue();
        } catch (RuntimeException e) {
            log.error("Report scheduler tick failed", e); // next tick retries; generation errors are stored per report
        }
    }
}
