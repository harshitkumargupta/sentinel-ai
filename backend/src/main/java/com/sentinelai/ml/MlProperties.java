package com.sentinelai.ml;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** ML scoring integration config. Disabled by default — the NoOp client is used until enabled. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.ml")
public class MlProperties {

    private boolean enabled = false;
    private String baseUrl = "http://localhost:8000";

    @Min(10)
    private int timeoutMs = 300;
    @Min(0)
    private int maxRetries = 1;

    /** Max points the ML factor can add to a risk score (model never dominates hard rules). */
    @Min(1)
    private int weightCap = 30;

    /** Circuit breaker: open after this many consecutive failures, for this cooldown. */
    @Min(1)
    private int circuitFailureThreshold = 3;
    @Min(1)
    private int circuitResetSeconds = 30;

    /** Drift: alert when any feature's live mean deviates this many std-devs from training. */
    private double driftSigmaThreshold = 3.0;
    private String driftCron = "0 */15 * * * *";
    private int driftSampleSize = 200;
}
