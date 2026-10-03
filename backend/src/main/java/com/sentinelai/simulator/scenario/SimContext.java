package com.sentinelai.simulator.scenario;

import java.time.Instant;
import java.util.Random;

/**
 * Deterministic context for scenario generation: a fixed time anchor, intensity, and a per-scenario
 * seeded {@link Random}. Everything derives from the seed, so runs are reproducible.
 */
public final class SimContext {

    /** Fixed anchor so generated timestamps depend only on the seed, not wall-clock time. */
    public static final Instant BASE = Instant.parse("2026-02-01T12:00:00Z");
    /** An "odd hour" (03:00 UTC) anchor for off-hours scenarios. */
    public static final Instant ODD_HOUR = Instant.parse("2026-02-01T03:00:00Z");

    private final long seed;
    private final int intensity;

    public SimContext(long seed, int intensity) {
        this.seed = seed;
        this.intensity = intensity;
    }

    public int intensity() {
        return intensity;
    }

    public Random rng(String scenarioId) {
        return new Random(seed * 1_000_003L + scenarioId.hashCode());
    }
}
