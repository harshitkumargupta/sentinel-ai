package com.sentinelai.detection.engine;

import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Read-side helpers a {@link DetectionStrategy} uses to inspect event history. Backed by the
 * event store; time comes from an injected {@link java.time.Clock} for deterministic tests.
 */
public interface DetectionContext {

    Instant now();

    /** Count events in an org of a type grouped by a field equal to {@code value}, since {@code since}. */
    long countInWindow(Long orgId, EventType type, GroupBy groupBy, String value, Instant since);

    /** The matching events (newest first, capped at {@code limit}) for the same window. */
    List<SecurityEvent> eventsInWindow(Long orgId, EventType type, GroupBy groupBy, String value,
                                       Instant since, int limit);

    /** Most recent prior event for a username (with a geo country), excluding the given event id. */
    Optional<SecurityEvent> latestForUserBefore(Long orgId, String username, Long excludeEventId);
}
