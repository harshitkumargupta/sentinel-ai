package com.sentinelai.playbook.domain;

import com.sentinelai.auth.domain.User;
import com.sentinelai.common.domain.Severity;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_by")
    private User proposedBy;

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
    @Column(nullable = false,
            columnDefinition = "enum('PROPOSED','APPROVED','EXECUTED','ROLLED_BACK','REJECTED','FAILED','EXPIRED')")
    private PlaybookActionStatus status;

    /** Risk level of this action (from the incident severity); drives approver rules. */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", columnDefinition = "enum('LOW','MEDIUM','HIGH','CRITICAL')")
    private Severity riskLevel;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dry_run_result", columnDefinition = "json")
    private String dryRunResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "json")
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "json")
    private String afterState;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "rolled_back_at")
    private Instant rolledBackAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
