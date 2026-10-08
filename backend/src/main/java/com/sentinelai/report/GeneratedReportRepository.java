package com.sentinelai.report;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GeneratedReportRepository extends JpaRepository<GeneratedReport, Long> {

    /** Metadata only (no file content) for the list view. */
    @Query("""
            select new com.sentinelai.report.ReportSummary(r.id, r.reportType, r.format, r.rangeFrom, r.rangeTo,
                   r.status, r.rowCount, r.sizeBytes, r.error, r.scheduleId, r.createdAt)
            from GeneratedReport r where r.orgId = :orgId order by r.id desc""")
    List<ReportSummary> summaries(@Param("orgId") Long orgId, Pageable pageable);
}
