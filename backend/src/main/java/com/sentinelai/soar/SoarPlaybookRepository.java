package com.sentinelai.soar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SoarPlaybookRepository extends JpaRepository<SoarPlaybook, Long> {

    List<SoarPlaybook> findByOrgIdOrderByNameAsc(Long orgId);

    boolean existsByOrgIdAndName(Long orgId, String name);
}
