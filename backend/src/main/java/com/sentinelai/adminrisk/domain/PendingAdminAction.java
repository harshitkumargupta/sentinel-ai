package com.sentinelai.adminrisk.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** A HIGH-risk admin action held for approval by a different admin. */
@Entity
@Table(name = "pending_admin_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingAdminAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String payload;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_breakdown", columnDefinition = "json")
    private String riskBreakdown;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('PENDING','APPROVED','REJECTED','EXPIRED','EXECUTED')")
    private PendingActionStatus status;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
