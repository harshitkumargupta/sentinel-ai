package com.sentinelai.alert.repository;

import com.sentinelai.alert.domain.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    Page<Alert> findByOrg_IdOrderByIdDesc(Long orgId, Pageable pageable);

    List<Alert> findByRunId(String runId);

    long countByOrg_Id(Long orgId);

    @Query("select a.mitreTechnique, count(a) from Alert a where a.org.id = :orgId group by a.mitreTechnique")
    List<Object[]> countByMitreTechnique(@Param("orgId") Long orgId);
}
