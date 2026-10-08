package com.sentinelai.coverage;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoverageRunRepository extends JpaRepository<CoverageRun, Long> {
    List<CoverageRun> findByOrgIdOrderByIdDesc(Long orgId, Pageable pageable);
}
