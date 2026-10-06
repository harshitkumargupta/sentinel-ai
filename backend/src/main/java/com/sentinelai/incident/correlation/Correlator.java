package com.sentinelai.incident.correlation;

import com.sentinelai.alert.domain.Alert;
import com.sentinelai.incident.domain.Incident;

/**
 * Groups alerts into incidents. The default implementation correlates by entity (user/IP) within a
 * time window and chains related rule types for the same entity into one incident.
 */
public interface Correlator {

    /**
     * Correlate an alert into a new or existing incident. Idempotent: re-correlating the same alert
     * never duplicates links.
     *
     * @return the incident the alert now belongs to, or null if correlation is disabled
     */
    Incident correlate(Alert alert);
}
