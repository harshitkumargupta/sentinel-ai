package com.sentinelai.report;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** One generated report file (stored in the DB so it survives restarts; size-capped). */
@Entity
@Table(name = "generated_reports")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedReport {

    public enum Status { COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false,
            columnDefinition = "enum('INCIDENT_SUMMARY','TOP_ATTACKERS','ALERTS_BY_MITRE','RESPONSE_ACTIONS')")
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('PDF','CSV')")
    private ReportFormat format;

    @Column(name = "range_from", nullable = false)
    private Instant rangeFrom;

    @Column(name = "range_to", nullable = false)
    private Instant rangeTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('COMPLETED','FAILED')")
    private Status status;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "longblob")
    private byte[] content;

    @Column(length = 500)
    private String error;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
