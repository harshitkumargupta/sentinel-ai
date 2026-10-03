package com.sentinelai.detection.engine;

import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Backtest {@link RuleContext}: fully in-memory and isolated from live state. The caller replays
 * stored events in ascending time order, calling {@link #advance} before evaluating each one, so
 * all window computations reflect the event's own point in time (deterministic, no DB writes).
 */
public class BacktestRuleContext implements RuleContext {

    private final List<SecurityEvent> seen = new ArrayList<>();
    private Instant currentTime = Instant.EPOCH;

    public void advance(SecurityEvent event) {
        seen.add(event);
        currentTime = event.getEventTimestamp();
    }

    @Override
    public boolean dryRun() {
        return true;
    }

    @Override
    public Instant now() {
        return currentTime;
    }

    @Override
    public long countInWindow(Long orgId, EventType type, GroupBy by, String value, int windowSeconds) {
        Instant since = currentTime.minusSeconds(windowSeconds);
        return seen.stream()
                .filter(e -> e.getEventType() == type)
                .filter(e -> value.equals(by.valueOf(e)))
                .filter(e -> !e.getEventTimestamp().isBefore(since) && !e.getEventTimestamp().isAfter(currentTime))
                .count();
    }

    @Override
    public long countDistinctUsers(Long orgId, EventType type, String sourceIp, int windowSeconds) {
        Instant since = currentTime.minusSeconds(windowSeconds);
        Set<String> users = new HashSet<>();
        for (SecurityEvent e : seen) {
            if (e.getEventType() == type && sourceIp.equals(e.getSourceIp())
                    && !e.getEventTimestamp().isBefore(since) && e.getUsername() != null) {
                users.add(e.getUsername());
            }
        }
        return users.size();
    }

    @Override
    public List<Long> recentEventIds(Long orgId, EventType type, GroupBy by, String value,
                                     int windowSeconds, int limit) {
        Instant since = currentTime.minusSeconds(windowSeconds);
        return seen.stream()
                .filter(e -> e.getEventType() == type)
                .filter(e -> value.equals(by.valueOf(e)))
                .filter(e -> !e.getEventTimestamp().isBefore(since))
                .sorted((a, b) -> b.getEventTimestamp().compareTo(a.getEventTimestamp()))
                .limit(limit)
                .map(SecurityEvent::getId)
                .toList();
    }

    @Override
    public Optional<SecurityEvent> previousUserEvent(Long orgId, String username, Long excludeEventId) {
        return seen.stream()
                .filter(e -> username.equals(e.getUsername()) && e.getGeoCountry() != null)
                .filter(e -> excludeEventId == null || !excludeEventId.equals(e.getId()))
                .max((a, b) -> a.getEventTimestamp().compareTo(b.getEventTimestamp()));
    }
}
