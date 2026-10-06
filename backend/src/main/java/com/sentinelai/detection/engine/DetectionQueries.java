package com.sentinelai.detection.engine;

import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** DB-backed history lookups shared by the live rule context. */
@Component
@RequiredArgsConstructor
public class DetectionQueries {

    private final SecurityEventRepository eventRepository;

    public long countDistinctUsers(Long orgId, EventType type, String sourceIp, Instant since) {
        return eventRepository.countDistinctUsers(orgId, type, sourceIp, since);
    }

    public List<Long> recentEventIds(Long orgId, EventType type, GroupBy by, String value,
                                     Instant since, int limit) {
        return eventRepository.findAll(windowSpec(orgId, type, by, value, since),
                        PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "eventTimestamp")))
                .map(SecurityEvent::getId).getContent();
    }

    public Optional<SecurityEvent> previousUserEvent(Long orgId, String username, Long excludeEventId) {
        Specification<SecurityEvent> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("org").get("id"), orgId));
            p.add(cb.equal(root.get("username"), username));
            p.add(cb.isNotNull(root.get("geoCountry")));
            if (excludeEventId != null) {
                p.add(cb.notEqual(root.get("id"), excludeEventId));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return eventRepository.findAll(spec,
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "eventTimestamp")))
                .stream().findFirst();
    }

    private Specification<SecurityEvent> windowSpec(Long orgId, EventType type, GroupBy by,
                                                    String value, Instant since) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("org").get("id"), orgId),
                cb.equal(root.get("eventType"), type),
                cb.equal(root.get(by.attribute()), value),
                cb.greaterThanOrEqualTo(root.get("eventTimestamp"), since));
    }
}
