package com.sentinelai.soar;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** One execution of a playbook on an incident, with per-step results. */
@Entity
@Table(name = "soar_runs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoarRun {

    public enum Status { SUCCEEDED, PARTIAL, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "playbook_id")
    private Long playbookId;

    @Column(name = "incident_id")
    private Long incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('SUCCEEDED','PARTIAL','FAILED')")
    private Status status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_results", nullable = false, columnDefinition = "json")
    private String stepResults;

    @Column(name = "triggered_by", nullable = false, length = 100)
    private String triggeredBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
