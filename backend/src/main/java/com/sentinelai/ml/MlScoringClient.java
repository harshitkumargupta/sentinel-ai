package com.sentinelai.ml;

import java.util.List;
import java.util.Optional;

/**
 * Client for the Python ML scoring service. Implementations must never throw into callers — a
 * failure (service down, timeout, circuit open, or disabled) returns {@link Optional#empty()} so
 * risk scoring degrades gracefully to rules only.
 */
public interface MlScoringClient {

    /** @param kind "entity" or "admin"; features must match that model's spec order. */
    Optional<MlScore> score(List<Double> features, String kind);

    Optional<MlModelInfo> modelInfo();
}
