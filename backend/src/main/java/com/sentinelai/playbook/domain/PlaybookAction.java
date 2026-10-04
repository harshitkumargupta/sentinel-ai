package com.sentinelai.playbook.domain;

import com.sentinelai.auth.domain.User;
import com.sentinelai.incident.domain.Incident;
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
 * A response action proposed for an incident, with human-in-the-loop approval and a
 * dry-run result captured before execution.
 */
@Entity
@Table(name = "playbook_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaybookAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "action_type", nullable = false, length = 100)
    private String actionType;

    @Column(name = "target_ref", length = 255)
    private String targetRef;

    @Column(length = 1000)
    private String reason;

    /** The AI analysis this action was proposed from (nullable; SET NULL on analysis delete). */
    @Column(name = "analysis_id")
    private Long analysisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('PROPOSED','APPROVED','EXECUTED','ROLLED_BACK')")
    private PlaybookActionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dry_run_result", columnDefinition = "json")
    private String dryRunResult;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "executed_at")
    private Instant executedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
