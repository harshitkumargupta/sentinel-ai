package com.sentinelai.audit.domain;

import com.sentinelai.common.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Immutable, insert-only audit record with a tamper-evident hash chain
 * ({@code prev_hash} -> {@code entry_hash}). {@code actor_id} is a plain id so the trail is
 * decoupled from the user aggregate and survives user deletion (FK is {@code ON DELETE SET NULL}).
 * The repository deliberately exposes no update or delete operations.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization org;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "prev_hash", length = 64, columnDefinition = "char(64)")
    private String prevHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "entry_hash", length = 64, columnDefinition = "char(64)")
    private String entryHash;

    // Set explicitly by AuditService so it is part of the hash chain (not DB/Hibernate-generated).
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
