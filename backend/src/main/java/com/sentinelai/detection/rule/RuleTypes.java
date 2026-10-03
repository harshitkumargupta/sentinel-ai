package com.sentinelai.detection.rule;

/** Canonical {@code detection_rules.rule_type} values handled by the built-in rules. */
public final class RuleTypes {

    public static final String BRUTE_FORCE = "BRUTE_FORCE";
    public static final String CREDENTIAL_STUFFING = "CREDENTIAL_STUFFING";
    public static final String HIGH_FREQUENCY_API = "HIGH_FREQUENCY_API";
    public static final String SUSPICIOUS_LOGIN = "SUSPICIOUS_LOGIN";
    public static final String IMPOSSIBLE_TRAVEL = "IMPOSSIBLE_TRAVEL";
    public static final String ABNORMAL_ACCESS = "ABNORMAL_ACCESS";
    public static final String HONEYTOKEN = "HONEYTOKEN";

    private RuleTypes() {
    }
}
