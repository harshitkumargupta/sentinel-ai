package com.sentinelai.incident.repository;

import com.sentinelai.incident.domain.IncidentAlert;
import com.sentinelai.incident.domain.IncidentAlertId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentAlertRepository extends JpaRepository<IncidentAlert, IncidentAlertId> {

    List<IncidentAlert> findById_IncidentId(Long incidentId);

    List<IncidentAlert> findById_AlertId(Long alertId);

    boolean existsById_AlertId(Long alertId);

    long countById_IncidentId(Long incidentId);
}
