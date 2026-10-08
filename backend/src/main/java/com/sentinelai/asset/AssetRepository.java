package com.sentinelai.asset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findByOrg_IdOrderByCriticalityDescHostnameAsc(Long orgId);

    Optional<Asset> findByOrg_IdAndHostnameIgnoreCase(Long orgId, String hostname);

    Optional<Asset> findByOrg_IdAndIp(Long orgId, String ip);
}
