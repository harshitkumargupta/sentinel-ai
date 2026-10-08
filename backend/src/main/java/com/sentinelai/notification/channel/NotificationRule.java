package com.sentinelai.notification.channel;

import com.sentinelai.common.domain.Severity;
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

/** When to notify (severity ≥ min, optional detection rule type, created/escalated) and which channels. */
@Entity
@Table(name = "notification_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_severity", nullable = false, columnDefinition = "enum('LOW','MEDIUM','HIGH','CRITICAL')")
    private Severity minSeverity;

    @Column(name = "rule_type", length = 50)
    private String ruleType;

    @Column(name = "on_incident_created", nullable = false)
    private boolean onIncidentCreated;

    @Column(name = "on_escalation", nullable = false)
    private boolean onEscalation;

    /** JSON array of channel ids. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channel_ids", nullable = false, columnDefinition = "json")
    private String channelIds;

    @Column(nullable = false)
    private boolean enabled;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
