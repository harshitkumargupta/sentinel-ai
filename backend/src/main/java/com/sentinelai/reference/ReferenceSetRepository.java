package com.sentinelai.reference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReferenceSetRepository extends JpaRepository<ReferenceSet, Long> {

    List<ReferenceSet> findByOrg_IdOrderByNameAsc(Long orgId);

    Optional<ReferenceSet> findByOrg_IdAndName(Long orgId, String name);

    boolean existsByOrg_IdAndName(Long orgId, String name);
}
