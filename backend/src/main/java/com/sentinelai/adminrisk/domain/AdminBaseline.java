package com.sentinelai.adminrisk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** A learned baseline metric for an admin (e.g. known_countries = ["US"]). */
@Entity
@Table(name = "admin_baselines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminBaseline {

    @EmbeddedId
    private AdminBaselineId id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String data;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
