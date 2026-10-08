package com.sentinelai.threatintel;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Offline threat intel: blocklist files bundled at {@code classpath:threatintel/*.txt}, plus an
 * optional local directory of extra {@code .txt} lists (never fetched from the network).
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.threat-intel")
public class ThreatIntelProperties {

    private boolean enabled = true;

    /** Optional extra directory of blocklist files; empty = bundled lists only. */
    private String directory = "";
}
