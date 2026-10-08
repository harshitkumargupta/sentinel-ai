package com.sentinelai.incident.domain;

import com.sentinelai.common.domain.Severity;

/** Analyst-set case priority (P1 = most urgent). Defaults from severity when an incident is created. */
public enum IncidentPriority {
    P1, P2, P3, P4;

    public static IncidentPriority fromSeverity(Severity severity) {
        if (severity == null) {
            return P3;
        }
        return switch (severity) {
            case CRITICAL -> P1;
            case HIGH -> P2;
            case MEDIUM -> P3;
            case LOW -> P4;
        };
    }
}
