package com.sentinelai.baseline.repository;

import com.sentinelai.baseline.domain.EntityBaseline;
import com.sentinelai.baseline.domain.EntityBaselineId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntityBaselineRepository extends JpaRepository<EntityBaseline, EntityBaselineId> {

    List<EntityBaseline> findById_EntityKey(String entityKey);
}
