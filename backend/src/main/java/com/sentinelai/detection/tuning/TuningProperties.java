package com.sentinelai.detection.tuning;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Detection-tuning config: minimum reviewed sample size and how far to sweep a threshold. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.tuning")
public class TuningProperties {

    /** Minimum reviewed (fired TP+FP) events before a suggestion is offered. */
    @Min(1)
    private int minSamples = 20;

    /** How many integer steps above the current threshold to evaluate. */
    @Min(1)
    private int maxThresholdDelta = 5;
}
