package com.sentinelai.incident.domain;

/**
 * Analyst feedback on an incident (drives detection tuning). Order must match the
 * {@code ENUM(...)} in migration {@code V5__incidents.sql}.
 */
public enum IncidentFeedback {
    TRUE_POSITIVE,
    FALSE_POSITIVE,
    UNREVIEWED
}
