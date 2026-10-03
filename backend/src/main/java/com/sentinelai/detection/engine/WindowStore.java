package com.sentinelai.detection.engine;

import java.time.Instant;

/**
 * Sliding-window counter keyed by an opaque string. In-memory now; a Redis-backed implementation
 * can replace it later without touching the rules (they only see {@link RuleContext}).
 */
public interface WindowStore {

    /** Record an observation of {@code key} at {@code timestamp}. */
    void record(String key, Instant timestamp);

    /** Count observations of {@code key} with timestamp in [{@code from}, {@code to}]. */
    long count(String key, Instant from, Instant to);
}
