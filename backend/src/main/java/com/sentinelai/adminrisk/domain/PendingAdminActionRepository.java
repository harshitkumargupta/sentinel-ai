package com.sentinelai.adminrisk.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PendingAdminActionRepository extends JpaRepository<PendingAdminAction, Long> {

    List<PendingAdminAction> findByOrgIdAndStatusOrderByIdDesc(Long orgId, PendingActionStatus status);
}
