package com.sentinelai.simulator.scenario;

import java.util.Map;

/**
 * A simulated event plus its ground-truth label. {@code payload} is a generic-normalizer map;
 * generation is deterministic (seed only), so two runs with the same seed produce equal lists.
 */
public record GeneratedEvent(
        Map<String, Object> payload,
        String scenarioId,
        boolean attack,
        String expectedRule) {
}
