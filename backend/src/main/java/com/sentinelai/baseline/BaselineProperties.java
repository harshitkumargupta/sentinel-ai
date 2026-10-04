package com.sentinelai.baseline;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Behavioral-baseline config. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.baseline")
public class BaselineProperties {

    /** Minimum observations before a baseline is trusted (cold-start entities are skipped). */
    @Min(2)
    private int minSamples = 30;

    private double zThreshold = 3.0;

    private String flushCron = "0 */5 * * * *";
}
