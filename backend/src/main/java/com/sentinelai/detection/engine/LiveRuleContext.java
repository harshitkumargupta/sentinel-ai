package com.sentinelai.detection.engine;

import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Live {@link RuleContext}: frequency counts come from the {@link WindowStore}; distinct-user,
 * recent-id and previous-event lookups come from the database.
 */
@Component
@RequiredArgsConstructor
public class LiveRuleContext implements RuleContext {

    private final WindowStore windowStore;
    private final DetectionQueries queries;
    private final Clock clock;

    // Windows are evaluated relative to the event being processed (set per event, per thread),
    // so detection is consistent with backtests and robust to historical/simulated timestamps.
    private final ThreadLocal<Instant> currentEventTime = new ThreadLocal<>();

    /** Record an event into the window store under each grouping dimension it has a value for. */
    public void observe(SecurityEvent event) {
        currentEventTime.set(event.getEventTimestamp());
        for (GroupBy by : GroupBy.values()) {
            String value = by.valueOf(event);
            if (value != null && !value.isBlank()) {
                windowStore.record(WindowKeys.of(event.getEventType(), by, value), event.getEventTimestamp());
            }
        }
    }

    @Override
    public Instant now() {
        Instant t = currentEventTime.get();
        return t != null ? t : clock.instant();
    }

    @Override
    public long countInWindow(Long orgId, EventType type, GroupBy by, String value, int windowSeconds) {
        Instant to = now();
        return windowStore.count(WindowKeys.of(type, by, value), to.minusSeconds(windowSeconds), to);
    }

    @Override
    public long countDistinctUsers(Long orgId, EventType type, String sourceIp, int windowSeconds) {
        return queries.countDistinctUsers(orgId, type, sourceIp, now().minusSeconds(windowSeconds));
    }

    @Override
    public List<Long> recentEventIds(Long orgId, EventType type, GroupBy by, String value,
                                     int windowSeconds, int limit) {
        return queries.recentEventIds(orgId, type, by, value, now().minusSeconds(windowSeconds), limit);
    }

    @Override
    public Optional<SecurityEvent> previousUserEvent(Long orgId, String username, Long excludeEventId) {
        return queries.previousUserEvent(orgId, username, excludeEventId);
    }
}
