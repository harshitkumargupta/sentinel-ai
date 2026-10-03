package com.sentinelai.adminrisk.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminBaselineRepository extends JpaRepository<AdminBaseline, AdminBaselineId> {

    List<AdminBaseline> findById_UserId(Long userId);
}
