package com.sentinelai.adminrisk;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

/** Weights, thresholds and band cutoffs for admin-action risk — all configurable. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.admin-risk")
public class AdminRiskProperties {

    private int cap = 100;
    private int mediumAt = 25;
    private int highAt = 50;
    private int criticalAt = 80;

    private int offHoursPoints = 15;
    private int newCountryPoints = 25;
    private int newIpPoints = 10;
    private int newDevicePoints = 10;
    private int sensitiveActionPoints = 20;
    private int privilegeEscalationPoints = 35;
    private int unusualSitePoints = 15;

    /** Burst: this many admin actions within the window adds points. */
    private int burstThreshold = 5;
    private int burstWindowSeconds = 300;
    private int burstPoints = 20;

    /** Peer deviation: acting at more than this multiple of the peer average adds points. */
    private double peerDeviationMultiple = 2.0;
    private int peerDeviationPoints = 15;

    private int pendingExpiryMinutes = 15;

    /** Actions considered sensitive / destructive. */
    private Set<String> sensitiveActions = Set.of(
            "USER_DISABLE", "USER_DELETE", "USER_CREATE", "ROLE_CHANGE",
            "RULE_DELETE", "RULE_DISABLE", "API_KEY_REVOKE", "SESSION_REVOKE");

    public RiskBand bandFor(int score) {
        if (score >= criticalAt) {
            return RiskBand.CRITICAL;
        }
        if (score >= highAt) {
            return RiskBand.HIGH;
        }
        if (score >= mediumAt) {
            return RiskBand.MEDIUM;
        }
        return RiskBand.LOW;
    }
}
