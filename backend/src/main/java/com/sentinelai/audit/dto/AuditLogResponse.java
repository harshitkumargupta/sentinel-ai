package com.sentinelai.audit.dto;

import com.sentinelai.audit.domain.AuditLog;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long orgId,
        Long actorId,
        String action,
        String entityType,
        Long entityId,
        String details,
        String ipAddress,
        String prevHash,
        String entryHash,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(
                a.getId(), a.getOrg().getId(), a.getActorId(), a.getAction(), a.getEntityType(),
                a.getEntityId(), a.getDetails(), a.getIpAddress(), a.getPrevHash(), a.getEntryHash(),
                a.getCreatedAt());
    }
}
