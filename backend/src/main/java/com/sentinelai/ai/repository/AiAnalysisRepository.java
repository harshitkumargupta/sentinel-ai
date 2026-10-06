package com.sentinelai.ai.repository;

import com.sentinelai.ai.domain.AiAnalysis;
import com.sentinelai.ai.domain.AnalysisState;
import com.sentinelai.ai.domain.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    List<AiAnalysis> findByIncident_Id(Long incidentId);

    List<AiAnalysis> findByIncident_IdOrderByIdDesc(Long incidentId);

    List<AiAnalysis> findByStatus(AnalysisStatus status);

    @Query("select a from AiAnalysis a where a.incident.org.id = :orgId "
            + "and a.analysisState = com.sentinelai.ai.domain.AnalysisState.COMPLETE")
    List<AiAnalysis> findCompleteByOrg(@Param("orgId") Long orgId);

    /** Idempotency: an already-completed analysis for the same incident + context can be reused. */
    Optional<AiAnalysis> findFirstByIncident_IdAndContextHashAndAnalysisStateOrderByIdDesc(
            Long incidentId, String contextHash, AnalysisState analysisState);
}
