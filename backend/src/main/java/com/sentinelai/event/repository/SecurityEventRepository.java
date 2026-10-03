package com.sentinelai.event.repository;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
}
