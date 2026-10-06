package com.sentinelai.common.domain;

/**
 * Shared severity scale used by security events, incidents, and detection rules.
 * Order must match the {@code ENUM(...)} definition in the Flyway migrations.
 */
public enum Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
