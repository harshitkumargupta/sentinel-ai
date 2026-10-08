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
    public static final String PORT_SCAN = "PORT_SCAN";
    public static final String SQL_INJECTION = "SQL_INJECTION";
    public static final String MALWARE = "MALWARE";
    public static final String PRIVILEGE_ESCALATION = "PRIVILEGE_ESCALATION";
    public static final String DATA_EXFILTRATION = "DATA_EXFILTRATION";
    public static final String PHISHING = "PHISHING";
    public static final String DDOS = "DDOS";

    private RuleTypes() {
    }
}
