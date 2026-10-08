package com.sentinelai.event.domain;

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

@Entity
@Table(name = "security_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization org;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private com.sentinelai.site.domain.Site site;

    // Optional client-supplied id for idempotent ingestion (unique per org when present).
    @Column(name = "client_event_id", length = 100)
    private String clientEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false,
            columnDefinition = "enum('FAILED_LOGIN','BRUTE_FORCE','SUSPICIOUS_LOGIN','API_ABUSE','ABNORMAL_ACCESS','HONEYTOKEN_ACCESS','OTHER','PROMPT_INJECTION','LOGIN_SUCCESS','PORT_SCAN','SQL_INJECTION','MALWARE_DETECTED','PRIVILEGE_ESCALATION','DATA_TRANSFER','PHISHING_CLICK','NETWORK_FLOOD')")
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('LOW','MEDIUM','HIGH','CRITICAL')")
    private Severity severity;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(length = 100)
    private String username;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(length = 255)
    private String resource;

    @Column(name = "asset_criticality")
    private Byte assetCriticality;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "json")
    private String rawPayload;

    @Column(name = "geo_country", length = 2)
    private String geoCountry;

    @Column(name = "geo_city", length = 100)
    private String geoCity;

    @Column(name = "is_honeytoken", nullable = false)
    private boolean honeytoken;

    @Column(name = "entity_key", length = 255)
    private String entityKey;

    @Column(name = "correlation_key", length = 255)
    private String correlationKey;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @CreationTimestamp
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;
}
