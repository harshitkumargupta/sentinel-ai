package com.sentinelai.detection.engine;

import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;

import java.util.List;
import java.util.Optional;

/**
 * Everything a {@link DetectionRuleEvaluator} needs to inspect history, independent of whether it
 * runs live (window store + DB) or in a backtest (replay buffer). All windows are expressed in
 * terms of event timestamps so results are deterministic.
 */
public interface RuleContext {

    java.time.Instant now();

    /** Count events of a type grouped by an attribute value within the last {@code windowSeconds}. */
    long countInWindow(Long orgId, EventType type, GroupBy by, String value, int windowSeconds);

    /** Distinct usernames seen for a type from one source IP within the window (credential stuffing). */
    long countDistinctUsers(Long orgId, EventType type, String sourceIp, int windowSeconds);

    /** Ids of the matching events in the window (newest first, capped). */
    List<Long> recentEventIds(Long orgId, EventType type, GroupBy by, String value,
                              int windowSeconds, int limit);

    /** The user's most recent prior event (with a geo country), excluding the current event. */
    Optional<SecurityEvent> previousUserEvent(Long orgId, String username, Long excludeEventId);

    /** True during a backtest (dry run): rules must avoid side effects such as counter bumps. */
    default boolean dryRun() {
        return false;
    }
}
