package com.sentinelai.adminrisk.web;

import com.sentinelai.adminrisk.domain.PendingActionStatus;
import com.sentinelai.adminrisk.domain.PendingAdminAction;
import com.sentinelai.auth.domain.RefreshToken;

import java.time.Instant;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record PendingActionResponse(Long id, String action, String entityType, Long entityId,
                                        Long requestedBy, int riskScore, PendingActionStatus status,
                                        Instant expiresAt, Instant createdAt) {
        public static PendingActionResponse from(PendingAdminAction p) {
            return new PendingActionResponse(p.getId(), p.getAction(), p.getEntityType(), p.getEntityId(),
                    p.getRequestedBy(), p.getRiskScore(), p.getStatus(), p.getExpiresAt(), p.getCreatedAt());
        }
    }

    public record SessionResponse(Long id, Instant createdAt, Instant expiresAt, boolean revoked) {
        public static SessionResponse from(RefreshToken t) {
            return new SessionResponse(t.getId(), t.getCreatedAt(), t.getExpiresAt(), t.isRevoked());
        }
    }
}
