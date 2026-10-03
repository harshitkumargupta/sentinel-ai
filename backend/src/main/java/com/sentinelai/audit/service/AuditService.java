package com.sentinelai.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sentinelai.audit.domain.AuditLog;
import com.sentinelai.audit.repository.AuditLogRepository;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.common.util.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Appends tamper-evident entries to {@code audit_logs}: each entry's {@code entry_hash} is
 * {@code SHA-256(prev_hash + canonicalJson(entry))}, chaining to the previous entry.
 *
 * <p>Writes are serialized ({@code synchronized} + {@code REQUIRES_NEW}) so concurrent requests
 * cannot interleave and fork the chain. This is adequate for the single-instance dev deployment;
 * a multi-instance deployment would move the serialization to the database (e.g. a row lock).
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final String GENESIS = "GENESIS";

    // Canonicalizes JSON (sorted keys, compact) so the hash is stable regardless of how MySQL
    // stores/returns a JSON column (it reorders keys and adds whitespace).
    private static final ObjectMapper CANON = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final AuditLogRepository auditLogRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized AuditLog record(Long orgId, Long actorId, String action,
                                        String entityType, Long entityId,
                                        String detailsJson, String ipAddress) {
        String prevHash = auditLogRepository.findTopByOrderByIdDesc()
                .map(AuditLog::getEntryHash)
                .orElse(GENESIS);
        // Truncate to microseconds so the hashed value matches what DATETIME(6) stores and
        // returns on read-back (Instant.now() carries nanoseconds that MySQL would drop).
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        String canonical = canonical(orgId, actorId, action, entityType, entityId,
                detailsJson, ipAddress, createdAt);
        String entryHash = Hashing.sha256Hex(prevHash + canonical);

        return auditLogRepository.save(AuditLog.builder()
                .org(organizationRepository.getReferenceById(orgId))
                .actorId(actorId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(detailsJson)
                .ipAddress(ipAddress)
                .prevHash(prevHash)
                .entryHash(entryHash)
                .createdAt(createdAt)
                .build());
    }

    /**
     * Recomputes the whole chain.
     *
     * @return null if the chain is intact, otherwise the id of the first broken entry.
     */
    @Transactional(readOnly = true)
    public Long verifyChain() {
        String expectedPrev = GENESIS;
        for (AuditLog entry : auditLogRepository.findAllByOrderByIdAsc()) {
            String canonical = canonical(entry.getOrg().getId(), entry.getActorId(), entry.getAction(),
                    entry.getEntityType(), entry.getEntityId(), entry.getDetails(), entry.getIpAddress(),
                    entry.getCreatedAt());
            String expectedHash = Hashing.sha256Hex(expectedPrev + canonical);
            if (!expectedPrev.equals(nullToGenesis(entry.getPrevHash()))
                    || !expectedHash.equals(entry.getEntryHash())) {
                return entry.getId();
            }
            expectedPrev = entry.getEntryHash();
        }
        return null;
    }

    private static String nullToGenesis(String prevHash) {
        return prevHash == null ? GENESIS : prevHash;
    }

    private static String canonical(Long orgId, Long actorId, String action, String entityType,
                                    Long entityId, String detailsJson, String ipAddress, Instant createdAt) {
        // Deterministic, order-fixed representation of the hashed fields.
        return new StringBuilder()
                .append("org=").append(orgId)
                .append("|actor=").append(actorId)
                .append("|action=").append(action)
                .append("|entityType=").append(entityType)
                .append("|entityId=").append(entityId)
                .append("|details=").append(canonicalizeJson(detailsJson))
                .append("|ip=").append(ipAddress)
                .append("|at=").append(createdAt.toString())
                .toString();
    }

    private static String canonicalizeJson(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            // Parse to maps/lists then re-serialize with sorted keys, so logically-equal JSON
            // (whatever MySQL's formatting) always produces the same string.
            Object tree = CANON.readValue(json, Object.class);
            return CANON.writeValueAsString(tree);
        } catch (Exception e) {
            // Not valid JSON — hash the raw value as-is.
            return json;
        }
    }
}
