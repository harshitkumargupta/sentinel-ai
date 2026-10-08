package com.sentinelai.honeytoken.domain;

import com.sentinelai.common.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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
 * A decoy credential/resource. Only a hash of the secret value is stored; a match on access
 * raises a {@code HONEYTOKEN_ACCESS} event.
 */
@Entity
@Table(name = "honeytokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Honeytoken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization org;

    @Column(nullable = false, length = 50)
    private String type;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "value_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String valueHash;

    @Column(length = 255)
    private String description;

    /** What the decoy is (drives where tripwires look for it). */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('USERNAME','API_KEY','URL_PATH','OTHER')")
    private HoneytokenKind kind = HoneytokenKind.OTHER;

    /** Shown in the UI: full for usernames/paths, masked for API keys. */
    @Column(name = "display_value", length = 120)
    private String displayValue;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "triggered_count", nullable = false)
    private Integer triggeredCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
