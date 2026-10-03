package com.sentinelai.simulator.dto;

import java.util.List;

/** All optional: scenarios (default = all), seed (default 42), intensity (default from config). */
public record SimulatorRunRequest(List<String> scenarios, Long seed, Integer intensity) {
}
