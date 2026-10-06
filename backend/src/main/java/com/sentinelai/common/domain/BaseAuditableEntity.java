package com.sentinelai.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Base class for entities that carry creation and modification timestamps.
 *
 * <p>Uses Hibernate's {@link CreationTimestamp}/{@link UpdateTimestamp} so the values are
 * populated without needing Spring Data JPA auditing configuration (which keeps
 * {@code @DataJpaTest} slices working without extra wiring). The underlying columns also have
 * database defaults ({@code DEFAULT CURRENT_TIMESTAMP(6)} / {@code ON UPDATE}) as a backstop.
 */
@Getter
@MappedSuperclass
public abstract class BaseAuditableEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
