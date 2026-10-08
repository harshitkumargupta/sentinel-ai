package com.sentinelai.logsource;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Log Sources page tuning: the events-per-second window and when a source counts as idle. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.log-sources")
public class LogSourceProperties {

    /** Events per second are averaged over this many trailing seconds. */
    @Min(1)
    private int epsWindowSeconds = 60;

    /** A source with no event for this long is shown as IDLE instead of RECEIVING. */
    @Min(1)
    private int idleAfterMinutes = 15;
}
