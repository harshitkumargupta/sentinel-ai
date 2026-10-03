package com.sentinelai.alert.domain;

import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A detection rule firing. Alerts are the engine's output; incidents (grouping alerts) come in a
 * later phase. Rule identity is snapshotted ({@code ruleType}, {@code ruleVersion},
 * {@code mitreTechnique}) so the alert survives rule edits/deletion.
 */
@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization org;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_version")
    private Integer ruleVersion;

    @Column(name = "rule_type", nullable = false, length = 50)
    private String ruleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('LOW','MEDIUM','HIGH','CRITICAL')")
    private Severity severity;

    @Column(name = "mitre_technique", length = 20)
    private String mitreTechnique;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "entity_key", length = 255)
    private String entityKey;

    @Column(name = "triggering_event_id")
    private Long triggeringEventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_event_ids", columnDefinition = "json")
    private String matchedEventIds;

    @Column(name = "run_id", length = 64)
    private String runId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
