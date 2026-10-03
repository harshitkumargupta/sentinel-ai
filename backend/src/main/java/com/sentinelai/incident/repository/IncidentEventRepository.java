package com.sentinelai.incident.repository;

import com.sentinelai.incident.domain.IncidentEvent;
import com.sentinelai.incident.domain.IncidentEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentEventRepository extends JpaRepository<IncidentEvent, IncidentEventId> {

    List<IncidentEvent> findById_IncidentId(Long incidentId);

    List<IncidentEvent> findById_EventId(Long eventId);

    long countById_IncidentId(Long incidentId);
}
