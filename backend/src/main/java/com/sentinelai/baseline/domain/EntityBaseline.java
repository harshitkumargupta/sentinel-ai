package com.sentinelai.baseline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Rolling statistical baseline for a given entity/metric pair, used by anomaly detection
 * (e.g. a z-score against {@code mean_value} / {@code std_dev}).
 */
@Entity
@Table(name = "entity_baselines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntityBaseline {

    @EmbeddedId
    private EntityBaselineId id;

    @Column(name = "mean_value")
    private Double meanValue;

    @Column(name = "std_dev")
    private Double stdDev;

    @Column(name = "sample_count", nullable = false)
    private Long sampleCount;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
