package com.sentinelai.ai.repository;

import com.sentinelai.ai.domain.AiAnalysis;
import com.sentinelai.ai.domain.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    List<AiAnalysis> findByIncident_Id(Long incidentId);

    List<AiAnalysis> findByStatus(AnalysisStatus status);
}
