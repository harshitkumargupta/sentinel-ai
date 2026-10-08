package com.sentinelai.detection.buildingblock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuildingBlockRepository extends JpaRepository<BuildingBlock, Long> {

    List<BuildingBlock> findByOrg_IdOrderByNameAsc(Long orgId);

    Optional<BuildingBlock> findByOrg_IdAndName(Long orgId, String name);

    boolean existsByOrg_IdAndName(Long orgId, String name);
}
