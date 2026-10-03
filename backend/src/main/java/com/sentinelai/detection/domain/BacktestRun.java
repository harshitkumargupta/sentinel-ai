package com.sentinelai.detection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Result of replaying a detection rule over historical events to estimate its behavior.
 */
@Entity
@Table(name = "backtest_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private DetectionRule rule;

    @CreationTimestamp
    @Column(name = "run_at", nullable = false, updatable = false)
    private Instant runAt;

    @Column(name = "events_scanned")
    private Integer eventsScanned;

    @Column(name = "alerts_fired")
    private Integer alertsFired;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot", columnDefinition = "json")
    private String configSnapshot;
}
