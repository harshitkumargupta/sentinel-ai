package com.sentinelai.detection.config;

import com.sentinelai.common.domain.Severity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.EnumMap;
import java.util.Map;

/**
 * Engine-level detection knobs (rule parameters themselves live in each rule's {@code config}
 * JSON). All values are overridable per profile / env var — no magic numbers in the engine.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.detection")
public class DetectionProperties {

    /** Feature flag: when false, the engine evaluates nothing. */
    private boolean enabled = true;

    /** Hard cap on how many events a single incident will link in one evaluation. */
    @Min(1)
    private int maxLinkedEvents = 100;

    /** Base risk score assigned to a new incident, by the firing rule's severity. */
    @NotNull
    private Map<Severity, Integer> severityScores = defaultSeverityScores();

    public int scoreFor(Severity severity) {
        return severityScores.getOrDefault(severity, 0);
    }

    private static Map<Severity, Integer> defaultSeverityScores() {
        Map<Severity, Integer> m = new EnumMap<>(Severity.class);
        m.put(Severity.LOW, 25);
        m.put(Severity.MEDIUM, 50);
        m.put(Severity.HIGH, 75);
        m.put(Severity.CRITICAL, 100);
        return m;
    }
}
