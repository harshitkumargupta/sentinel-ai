package com.sentinelai.simulator.dto;

import com.sentinelai.simulator.TimeAnchor;

import java.util.List;

/**
 * All optional: scenarios (default = all), seed (default 42), intensity (default from config),
 * timeAnchor (default FIXED — see {@link TimeAnchor}).
 */
public record SimulatorRunRequest(List<String> scenarios, Long seed, Integer intensity, TimeAnchor timeAnchor) {

    public SimulatorRunRequest(List<String> scenarios, Long seed, Integer intensity) {
        this(scenarios, seed, intensity, null);
    }
}
