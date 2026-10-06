package com.sentinelai.event.service;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link Specification} for filtering security events (all filters optional, ANDed),
 * scoped to the caller's organization.
 */
public final class EventSpecifications {

    private EventSpecifications() {
    }

    public static Specification<SecurityEvent> build(Long orgId, String ip, String username,
                                                     EventType type, Severity severity,
                                                     Instant from, Instant to) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("org").get("id"), orgId));
            if (ip != null && !ip.isBlank()) {
                p.add(cb.equal(root.get("sourceIp"), ip));
            }
            if (username != null && !username.isBlank()) {
                p.add(cb.equal(root.get("username"), username));
            }
            if (type != null) {
                p.add(cb.equal(root.get("eventType"), type));
            }
            if (severity != null) {
                p.add(cb.equal(root.get("severity"), severity));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("eventTimestamp"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.get("eventTimestamp"), to));
            }
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
