package com.sentinelai.detection.support;

import com.sentinelai.detection.engine.GroupBy;
import com.sentinelai.detection.engine.RuleContext;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Hand-controlled {@link RuleContext} for rule unit tests. */
public class FakeRuleContext implements RuleContext {

    public long count;
    public long distinctUsers;
    public List<Long> recentIds = List.of();
    public SecurityEvent previous;
    public Instant now = Instant.parse("2026-02-01T12:00:00Z");

    @Override
    public Instant now() {
        return now;
    }

    @Override
    public long countInWindow(Long orgId, EventType type, GroupBy by, String value, int windowSeconds) {
        return count;
    }

    @Override
    public long countDistinctUsers(Long orgId, EventType type, String sourceIp, int windowSeconds) {
        return distinctUsers;
    }

    @Override
    public List<Long> recentEventIds(Long orgId, EventType type, GroupBy by, String value,
                                     int windowSeconds, int limit) {
        return recentIds;
    }

    @Override
    public Optional<SecurityEvent> previousUserEvent(Long orgId, String username, Long excludeEventId) {
        return Optional.ofNullable(previous);
    }
}
