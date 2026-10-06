package com.sentinelai.simulator.dto;

import com.sentinelai.simulator.domain.SimulatorRun;

import java.time.Instant;

public record SimulatorRunResponse(
        String runId,
        long seed,
        String scenarios,
        int intensity,
        int eventsGenerated,
        Instant createdAt) {

    public static SimulatorRunResponse from(SimulatorRun r) {
        return new SimulatorRunResponse(r.getRunId(), r.getSeed(), r.getScenarios(),
                r.getIntensity(), r.getEventsGenerated(), r.getCreatedAt());
    }
}
