package com.sentinelai.report;

import com.sentinelai.audit.service.AuditService;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.report.web.ReportDtos.SaveScheduleRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Daily/weekly report schedules. Each run covers the period since the previous run (or one
 * day/week for the first), then the next run is computed from the schedule, never from "now + 1",
 * so a late run doesn't drift. Run-now generates immediately without moving the schedule.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportScheduleService {

    private final ReportScheduleRepository repository;
    private final ReportService reportService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ReportSchedule> list(Long orgId) {
        return repository.findByOrgIdOrderByIdAsc(orgId);
    }

    @Transactional
    public ReportSchedule save(Long orgId, Long userId, Long id, SaveScheduleRequest req) {
        if (req.frequency() == ScheduleFrequency.WEEKLY && req.dayOfWeek() == null) {
            throw new BadRequestException("Weekly schedules need a day of week (1 = Monday … 7 = Sunday)");
        }
        ReportSchedule s = id == null ? new ReportSchedule() : load(orgId, id);
        s.setOrgId(orgId);
        s.setName(req.name().trim());
        s.setReportType(req.type());
        s.setFormat(req.format());
        s.setFrequency(req.frequency());
        s.setDayOfWeek(req.frequency() == ScheduleFrequency.WEEKLY ? req.dayOfWeek() : null);
        s.setHourUtc(req.hourUtc());
        s.setEnabled(req.enabled() == null || req.enabled());
        if (s.getCreatedBy() == null) {
            s.setCreatedBy(userId);
        }
        s.setNextRunAt(nextRun(s.getFrequency(), s.getDayOfWeek(), s.getHourUtc(), clock.instant()));
        ReportSchedule saved = repository.save(s);
        auditService.record(orgId, userId, id == null ? "REPORT_SCHEDULE_CREATE" : "REPORT_SCHEDULE_UPDATE",
                "report_schedule", saved.getId(), "{\"frequency\":\"" + saved.getFrequency() + "\"}", null);
        return saved;
    }

    @Transactional
    public void delete(Long orgId, Long userId, Long id) {
        repository.delete(load(orgId, id));
        auditService.record(orgId, userId, "REPORT_SCHEDULE_DELETE", "report_schedule", id, "{}", null);
    }

    /** Run now (button): generates for the schedule's period ending now; doesn't change next_run_at. */
    @Transactional
    public ReportSummary runNow(Long orgId, Long userId, Long id) {
        ReportSchedule s = load(orgId, id);
        Instant now = clock.instant();
        return reportService.generate(orgId, userId, s.getReportType(), s.getFormat(), periodStart(s, now), now, s.getId());
    }

    /** Called by the scheduler: run every enabled schedule whose time has come. */
    @Transactional
    public int runDue() {
        Instant now = clock.instant();
        int ran = 0;
        for (ReportSchedule s : repository.findByEnabledTrueAndNextRunAtLessThanEqual(now)) {
            reportService.generate(s.getOrgId(), s.getCreatedBy(), s.getReportType(), s.getFormat(),
                    periodStart(s, now), now, s.getId());
            s.setLastRunAt(now);
            s.setNextRunAt(nextRun(s.getFrequency(), s.getDayOfWeek(), s.getHourUtc(), now));
            repository.save(s);
            ran++;
        }
        if (ran > 0) {
            log.info("Ran {} scheduled report(s)", ran);
        }
        return ran;
    }

    private static Instant periodStart(ReportSchedule s, Instant now) {
        if (s.getLastRunAt() != null) {
            return s.getLastRunAt();
        }
        return now.minus(s.getFrequency() == ScheduleFrequency.WEEKLY ? 7 : 1, ChronoUnit.DAYS);
    }

    /** The next run strictly after {@code after}, at {@code hourUtc}:00 UTC (on {@code dayOfWeek} if weekly). */
    static Instant nextRun(ScheduleFrequency frequency, Integer dayOfWeek, int hourUtc, Instant after) {
        ZonedDateTime base = after.atZone(ZoneOffset.UTC);
        ZonedDateTime candidate = base.truncatedTo(ChronoUnit.DAYS).withHour(hourUtc);
        if (frequency == ScheduleFrequency.DAILY) {
            return (candidate.isAfter(base) ? candidate : candidate.plusDays(1)).toInstant();
        }
        DayOfWeek dow = DayOfWeek.of(dayOfWeek);
        ZonedDateTime weekly = candidate.with(TemporalAdjusters.nextOrSame(dow));
        if (!weekly.isAfter(base)) {
            weekly = weekly.plusWeeks(1);
        }
        return weekly.toInstant();
    }

    private ReportSchedule load(Long orgId, Long id) {
        return repository.findById(id).filter(s -> s.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Schedule not found: " + id));
    }
}
