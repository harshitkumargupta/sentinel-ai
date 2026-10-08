package com.sentinelai.report;

import com.sentinelai.audit.service.AuditService;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Generates a report (build → render → store) and serves stored files. A rendering failure is
 * recorded as a FAILED report with the reason rather than thrown, so a broken schedule can't loop.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportDataService dataService;
    private final CsvReportRenderer csv;
    private final PdfReportRenderer pdf;
    private final GeneratedReportRepository repository;
    private final ReportProperties props;
    private final AuditService auditService;

    public record Download(String filename, String mediaType, byte[] content) {
    }

    public void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new BadRequestException("'from' must be before 'to'");
        }
        if (Duration.between(from, to).toDays() > props.getMaxRangeDays()) {
            throw new BadRequestException("Date range is too long (max " + props.getMaxRangeDays() + " days)");
        }
    }

    @Transactional
    public ReportSummary generate(Long orgId, Long userId, ReportType type, ReportFormat format,
                                  Instant from, Instant to, Long scheduleId) {
        validateRange(from, to);
        GeneratedReport.GeneratedReportBuilder r = GeneratedReport.builder().orgId(orgId).reportType(type)
                .format(format).rangeFrom(from).rangeTo(to).scheduleId(scheduleId).createdBy(userId);
        try {
            ReportTable table = dataService.build(orgId, type, from, to);
            byte[] bytes = format == ReportFormat.PDF ? pdf.render(table) : csv.render(table);
            if (bytes.length > props.getMaxBytes()) {
                throw new IllegalStateException("report exceeds " + props.getMaxBytes() + " bytes; narrow the range");
            }
            r.status(GeneratedReport.Status.COMPLETED).rowCount(table.rows().size()).sizeBytes(bytes.length).content(bytes);
        } catch (RuntimeException e) {
            log.warn("Report {} {} for org {} failed: {}", type, format, orgId, e.toString());
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            r.status(GeneratedReport.Status.FAILED).error(msg.length() > 500 ? msg.substring(0, 500) : msg);
        }
        GeneratedReport saved = repository.save(r.build());
        auditService.record(orgId, userId, "REPORT_GENERATE", "report", saved.getId(),
                "{\"type\":\"" + type + "\",\"format\":\"" + format + "\",\"status\":\"" + saved.getStatus() + "\"}", null);
        return summary(saved);
    }

    @Transactional(readOnly = true)
    public List<ReportSummary> list(Long orgId) {
        return repository.summaries(orgId, PageRequest.of(0, 100));
    }

    @Transactional(readOnly = true)
    public Download download(Long orgId, Long id) {
        GeneratedReport r = load(orgId, id);
        if (r.getStatus() != GeneratedReport.Status.COMPLETED || r.getContent() == null) {
            throw new BadRequestException("This report failed and has no file: " + r.getError());
        }
        String name = "sentinel-%s-%s.%s".formatted(r.getReportType().name().toLowerCase().replace('_', '-'),
                r.getCreatedAt().toString().substring(0, 10), r.getFormat().extension());
        return new Download(name, r.getFormat().mediaType(), r.getContent());
    }

    @Transactional
    public void delete(Long orgId, Long userId, Long id) {
        repository.delete(load(orgId, id));
        auditService.record(orgId, userId, "REPORT_DELETE", "report", id, "{}", null);
    }

    private GeneratedReport load(Long orgId, Long id) {
        return repository.findById(id).filter(r -> r.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Report not found: " + id));
    }

    private static ReportSummary summary(GeneratedReport r) {
        return new ReportSummary(r.getId(), r.getReportType(), r.getFormat(), r.getRangeFrom(), r.getRangeTo(),
                r.getStatus(), r.getRowCount(), r.getSizeBytes(), r.getError(), r.getScheduleId(), r.getCreatedAt());
    }
}
