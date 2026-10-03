package com.sentinelai.incident.domain;

import com.sentinelai.auth.domain.User;
import com.sentinelai.common.domain.BaseAuditableEntity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization org;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private com.sentinelai.site.domain.Site site;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,
            columnDefinition = "enum('OPEN','INVESTIGATING','CONTAINED','RESOLVED','FALSE_POSITIVE')")
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('LOW','MEDIUM','HIGH','CRITICAL')")
    private Severity severity;

    @Column(name = "risk_score")
    private Integer riskScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_breakdown", columnDefinition = "json")
    private String riskBreakdown;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('TRUE_POSITIVE','FALSE_POSITIVE','UNREVIEWED')")
    private IncidentFeedback feedback;

    // Set by the detection engine to correlate repeated firings of the same rule/entity.
    @Column(name = "correlation_key", length = 255)
    private String correlationKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
