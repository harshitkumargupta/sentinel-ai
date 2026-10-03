package com.sentinelai.event.repository;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SecurityEventRepository
        extends JpaRepository<SecurityEvent, Long>, JpaSpecificationExecutor<SecurityEvent> {

    List<SecurityEvent> findByEventType(EventType eventType);

    List<SecurityEvent> findBySeverity(Severity severity);

    List<SecurityEvent> findBySourceIp(String sourceIp);

    List<SecurityEvent> findByUsername(String username);

    List<SecurityEvent> findByEventTimestampBetween(Instant from, Instant to);

    long countByOrg_IdAndSeverity(Long orgId, Severity severity);

    long countByOrg_IdAndEventType(Long orgId, EventType eventType);

    long countByOrg_IdAndEventTimestampAfter(Long orgId, Instant after);

    long countByOrg_Id(Long orgId);

    java.util.Optional<SecurityEvent> findByOrg_IdAndClientEventId(Long orgId, String clientEventId);

    List<SecurityEvent> findByOrg_IdAndEventTimestampBetweenOrderByEventTimestampAsc(
            Long orgId, Instant from, Instant to);

    @Query("""
            select count(distinct e.username) from SecurityEvent e
            where e.org.id = :orgId and e.eventType = :type and e.sourceIp = :sourceIp
              and e.eventTimestamp >= :since""")
    long countDistinctUsers(@Param("orgId") Long orgId,
                            @Param("type") EventType type,
                            @Param("sourceIp") String sourceIp,
                            @Param("since") Instant since);
}
