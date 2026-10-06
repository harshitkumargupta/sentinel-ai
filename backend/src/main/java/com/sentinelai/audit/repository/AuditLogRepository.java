package com.sentinelai.audit.repository;

import com.sentinelai.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Insert-only repository for the audit trail. It extends the base {@link Repository} marker
 * (not {@code JpaRepository} or {@code CrudRepository}) and deliberately exposes only save and
 * read operations — no update-in-place or delete — to keep the trail immutable.
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    Optional<AuditLog> findById(Long id);

    List<AuditLog> findAll();

    Page<AuditLog> findAllByOrderByIdDesc(Pageable pageable);

    List<AuditLog> findAllByOrderByIdAsc();

    Optional<AuditLog> findTopByOrderByIdDesc();

    long count();

    List<AuditLog> findByActorId(Long actorId);

    List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);

    long countByActorIdAndCreatedAtAfter(Long actorId, Instant after);

    long countByCreatedAtAfter(Instant after);

    @Query("""
            select a from AuditLog a
            where (:actorId is null or a.actorId = :actorId)
              and (:action is null or a.action = :action)
              and (:entityType is null or a.entityType = :entityType)
              and a.createdAt >= :from
            order by a.id desc""")
    Page<AuditLog> timeline(@Param("actorId") Long actorId,
                            @Param("action") String action,
                            @Param("entityType") String entityType,
                            @Param("from") Instant from,
                            Pageable pageable);
}
