package com.sentinelai.site;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Site-related tuning. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.site")
public class SiteProperties {

    /** Raise a "site silence" signal if a site has had no events for this many minutes. */
    @Min(1)
    private int silenceMinutes = 30;
}
