package com.sentinelai.baseline;

import java.util.Optional;

/**
 * Maintains rolling per-entity/metric baselines (Welford) with hot state in Redis and periodic
 * persistence to {@code entity_baselines}. Cold-start entities (below the min sample count) return
 * empty rather than a spurious deviation.
 */
public interface BehavioralBaselineService {

    void observe(String entityKey, String metric, double value);

    /** z-score for a value, or empty if the baseline isn't trusted yet (cold start). */
    Optional<Double> zScore(String entityKey, String metric, double value);

    Optional<Stat> stat(String entityKey, String metric);

    record Stat(long count, double mean, double std) {
    }
}
