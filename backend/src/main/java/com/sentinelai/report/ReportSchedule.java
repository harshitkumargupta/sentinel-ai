package com.sentinelai.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** A daily/weekly report schedule (UTC hour; weekly also has a day, 1 = Monday … 7 = Sunday). */
@Entity
@Table(name = "report_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false,
            columnDefinition = "enum('INCIDENT_SUMMARY','TOP_ATTACKERS','ALERTS_BY_MITRE','RESPONSE_ACTIONS')")
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('PDF','CSV')")
    private ReportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('DAILY','WEEKLY')")
    private ScheduleFrequency frequency;

    @Column(name = "day_of_week", columnDefinition = "tinyint")
    private Integer dayOfWeek;

    @Column(name = "hour_utc", nullable = false, columnDefinition = "tinyint")
    private int hourUtc;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
