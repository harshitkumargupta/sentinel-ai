package com.sentinelai.offense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentNoteRepository extends JpaRepository<IncidentNote, Long> {

    List<IncidentNote> findByIncident_IdOrderByIdAsc(Long incidentId);

    long countByIncident_Id(Long incidentId);
}
