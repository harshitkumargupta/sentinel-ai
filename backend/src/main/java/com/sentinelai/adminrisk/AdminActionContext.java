package com.sentinelai.adminrisk;

import java.time.Instant;

/**
 * Everything the admin-risk factors score over. Built by the guard (baselines loaded, burst/peer
 * counts computed) and passed to each {@link AdminActionRiskFactor} as a pure input.
 */
public record AdminActionContext(
        Long orgId,
        Long actorUserId,
        String actorUsername,
        String action,
        String entityType,
        Long entityId,
        String ipAddress,
        String country,
        String device,
        Long siteId,
        Instant at,
        String beforeJson,
        String afterJson,
        AdminBaselines baselines,
        long recentActionCount,
        double peerAverageActions) {
}
