package com.sentinelai.detection.repository;

import com.sentinelai.detection.domain.DetectionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DetectionRuleRepository extends JpaRepository<DetectionRule, Long> {

    Optional<DetectionRule> findByName(String name);

    List<DetectionRule> findByEnabledTrue();

    List<DetectionRule> findByOrg_IdAndEnabledTrue(Long orgId);

    boolean existsByName(String name);
}
