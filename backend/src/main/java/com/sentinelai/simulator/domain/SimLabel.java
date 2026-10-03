package com.sentinelai.simulator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Ground-truth label for a simulated event, used by the evaluation harness to score detection.
 */
@Entity
@Table(name = "sim_labels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "scenario_id", nullable = false, length = 50)
    private String scenarioId;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "is_attack", nullable = false)
    private boolean attack;

    @Column(name = "expected_rule", length = 50)
    private String expectedRule;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
