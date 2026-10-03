package com.sentinelai.audit.repository;

import com.sentinelai.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

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
}
