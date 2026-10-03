package com.sentinelai.incident.domain;

/**
 * Incident triage lifecycle. Order must match the {@code ENUM(...)} in
 * migration {@code V4__incidents.sql}.
 */
public enum IncidentStatus {
    OPEN,
    INVESTIGATING,
    CONTAINED,
    RESOLVED,
    FALSE_POSITIVE
}
