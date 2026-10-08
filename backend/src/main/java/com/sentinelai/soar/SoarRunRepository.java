package com.sentinelai.soar;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SoarRunRepository extends JpaRepository<SoarRun, Long> {

    List<SoarRun> findByOrgIdOrderByIdDesc(Long orgId, Pageable pageable);
}
