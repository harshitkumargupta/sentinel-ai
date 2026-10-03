package com.sentinelai.incident.correlation;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Correlation tuning — overridable per profile / env var. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.correlation")
public class CorrelationProperties {

    private boolean enabled = true;

    /** Alerts for the same entity within this window join the same open incident. */
    @Min(1)
    private int windowSeconds = 3600;
}
