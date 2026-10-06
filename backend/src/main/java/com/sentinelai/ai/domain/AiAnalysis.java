package com.sentinelai.ai.domain;

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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_analyses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false,
            columnDefinition = "enum('THREAT_ANALYSIS','CORRELATION','ROOT_CAUSE','RESPONSE_RECOMMENDATION','INVESTIGATION')")
    private AgentType agentType;

    @Column(columnDefinition = "text")
    private String prompt;

    @Column(columnDefinition = "text")
    private String output;

    @Column(precision = 5, scale = 2)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_event_ids", columnDefinition = "json")
    private String evidenceEventIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", columnDefinition = "enum('VALID','REJECTED','FALLBACK')")
    private ValidationStatus validationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_state", nullable = false,
            columnDefinition = "enum('QUEUED','RUNNING','COMPLETE','FAILED')")
    private AnalysisState analysisState;

    @Column(name = "template_version", length = 20)
    private String templateVersion;

    @Column(name = "context_hash", columnDefinition = "char(64)")
    private String contextHash;

    @Column(name = "faithfulness_score", precision = 5, scale = 2)
    private BigDecimal faithfulnessScore;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "cost_usd", precision = 10, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "injection_detected", nullable = false)
    private boolean injectionDetected;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('PENDING','APPROVED','REJECTED','MODIFIED')")
    private AnalysisStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
