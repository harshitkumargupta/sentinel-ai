package com.sentinelai.simulator;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Simulator feature flag + defaults. The simulator is off unless {@code enabled} is true. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.simulator")
public class SimulatorProperties {

    private boolean enabled = false;

    @Min(1)
    private int defaultIntensity = 5;
}
