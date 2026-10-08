package com.sentinelai.soar;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** A SOAR playbook: trigger (rule type and/or minimum severity) + ordered steps (JSON). */
@Entity
@Table(name = "soar_playbooks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoarPlaybook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "trigger_rule_type", length = 50)
    private String triggerRuleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_min_severity", columnDefinition = "enum('LOW','MEDIUM','HIGH','CRITICAL')")
    private Severity triggerMinSeverity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private String steps;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "auto_run", nullable = false)
    private boolean autoRun;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
