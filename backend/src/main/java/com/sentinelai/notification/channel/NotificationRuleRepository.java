package com.sentinelai.notification.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, Long> {
    List<NotificationRule> findByOrgIdOrderByIdAsc(Long orgId);

    List<NotificationRule> findByOrgIdAndEnabledTrue(Long orgId);
}
