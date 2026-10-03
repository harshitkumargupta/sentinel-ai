package com.sentinelai.incident.repository;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentRepository
        extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    List<Incident> findByStatus(IncidentStatus status);

    List<Incident> findBySeverity(Severity severity);

    List<Incident> findByAssignedTo_Id(Long userId);

    long countByOrg_IdAndStatus(Long orgId, IncidentStatus status);

    long countByOrg_IdAndSeverity(Long orgId, Severity severity);
}
