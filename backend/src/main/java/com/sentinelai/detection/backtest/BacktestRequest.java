package com.sentinelai.detection.backtest;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** {@code configOverride} is optional (defaults to the rule's current config). */
public record BacktestRequest(
        JsonNode configOverride,
        @NotNull Instant from,
        @NotNull Instant to) {
}
