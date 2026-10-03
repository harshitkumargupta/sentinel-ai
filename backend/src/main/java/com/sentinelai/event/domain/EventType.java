package com.sentinelai.event.domain;

/**
 * Catalog of security event types. Order must match the {@code ENUM(...)} in
 * migration {@code V3__security_events.sql}.
 */
public enum EventType {
    FAILED_LOGIN,
    BRUTE_FORCE,
    SUSPICIOUS_LOGIN,
    API_ABUSE,
    ABNORMAL_ACCESS,
    OTHER
}
