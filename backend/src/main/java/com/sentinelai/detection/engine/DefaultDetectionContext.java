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

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DefaultDetectionContext implements DetectionContext {

    private final SecurityEventRepository eventRepository;
    private final Clock clock;

    @Override
    public Instant now() {
        return clock.instant();
    }

    @Override
    public long countInWindow(Long orgId, EventType type, GroupBy groupBy, String value, Instant since) {
        return eventRepository.count(windowSpec(orgId, type, groupBy, value, since));
    }

    @Override
    public List<SecurityEvent> eventsInWindow(Long orgId, EventType type, GroupBy groupBy, String value,
                                              Instant since, int limit) {
        return eventRepository.findAll(windowSpec(orgId, type, groupBy, value, since),
                PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "eventTimestamp")))
                .getContent();
    }

    @Override
    public Optional<SecurityEvent> latestForUserBefore(Long orgId, String username, Long excludeEventId) {
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

    private Specification<SecurityEvent> windowSpec(Long orgId, EventType type, GroupBy groupBy,
                                                    String value, Instant since) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("org").get("id"), orgId));
            p.add(cb.equal(root.get("eventType"), type));
            p.add(cb.equal(root.get(groupBy.attribute()), value));
            p.add(cb.greaterThanOrEqualTo(root.get("eventTimestamp"), since));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
