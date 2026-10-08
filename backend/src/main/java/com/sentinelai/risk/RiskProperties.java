package com.sentinelai.risk;

import com.sentinelai.common.domain.Severity;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Risk weights, caps and severity cutoffs — all overridable per profile / env var (the editable
 * configuration surface for the risk model). The total score is capped at {@link #cap}.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.risk")
public class RiskProperties {

    @Min(1)
    private int cap = 100;

    /** Score ≥ value → that severity (checked CRITICAL→HIGH→MEDIUM, else LOW). */
    private int mediumAt = 25;
    private int highAt = 50;
    private int criticalAt = 80;

    /** SeverityFactor: points for the highest alert severity in the incident. */
    private Map<Severity, Integer> severityPoints = defaultSeverityPoints();

    private int frequencyPerEvent = 2;
    private int frequencyCap = 20;

    private int repetitionPerExtraAlert = 5;
    private int repetitionCap = 20;

    private int assetCriticalityPerLevel = 8;
    private int assetCriticalityCap = 32;
    /** Max points from open vulnerabilities on the incident's assets. */
    private int vulnerabilityCap = 20;

    private int honeytokenPoints = 60;

    private int newCountryPoints = 15;
    private int oddHourPoints = 10;
    private int firstTimeResourcePoints = 10;
    private int userBehaviorCap = 30;

    /** MitreStageFactor: points by technique id (later kill-chain stages weigh more). */
    private Map<String, Integer> mitreStageWeights = defaultMitreWeights();

    public Severity severityFor(int score) {
        if (score >= criticalAt) {
            return Severity.CRITICAL;
        }
        if (score >= highAt) {
            return Severity.HIGH;
        }
        if (score >= mediumAt) {
            return Severity.MEDIUM;
        }
        return Severity.LOW;
    }

    private static Map<Severity, Integer> defaultSeverityPoints() {
        Map<Severity, Integer> m = new EnumMap<>(Severity.class);
        m.put(Severity.LOW, 10);
        m.put(Severity.MEDIUM, 25);
        m.put(Severity.HIGH, 40);
        m.put(Severity.CRITICAL, 60);
        return m;
    }

    private static Map<String, Integer> defaultMitreWeights() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("T1110", 5);        // credential access (early)
        m.put("T1110.004", 5);
        m.put("T1499", 5);        // impact-ish / dos
        m.put("T1078", 15);       // valid accounts (access)
        m.put("T1078.001", 20);   // default/compromised accounts
        m.put("T1548", 20);       // privilege escalation (late)
        m.put("T1046", 3);        // discovery (recon)
        m.put("T1190", 10);       // initial access via public-facing app
        m.put("T1566.002", 10);   // initial access via phishing link
        m.put("T1204.002", 15);   // execution of a malicious file
        m.put("T1068", 20);       // privilege escalation via exploitation
        m.put("T1048", 25);       // exfiltration (final stage)
        m.put("T1498", 10);       // network denial of service
        return m;
    }
}
