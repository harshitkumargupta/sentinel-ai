package com.sentinelai.incident.repository;

import com.sentinelai.incident.domain.IncidentTimeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentTimelineRepository extends JpaRepository<IncidentTimeline, Long> {

    List<IncidentTimeline> findByIncidentIdOrderByIdAsc(Long incidentId);
}
