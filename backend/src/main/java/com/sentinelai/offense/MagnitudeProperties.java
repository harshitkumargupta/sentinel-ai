package com.sentinelai.offense;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Offense magnitude weights and inputs (QRadar-style: magnitude from severity, relevance and
 * credibility, each 0–10). All overridable per profile / env.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.offense.magnitude")
public class MagnitudeProperties {

    @Min(0) @Max(10)
    private int severityWeight = 3;
    @Min(0) @Max(10)
    private int relevanceWeight = 2;
    @Min(0) @Max(10)
    private int credibilityWeight = 1;

    /** Account names treated as privileged for relevance (in addition to ADMIN-role users). */
    private List<String> privilegedAccounts = List.of("root", "admin", "administrator", "sa");

    /** Offense lists compute magnitude over at most this many most-recent incidents. */
    @Min(10) @Max(5000)
    private int listScanLimit = 500;
}
