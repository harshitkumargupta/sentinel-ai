package com.sentinelai.kafka.message;

/** Carried on {@code incidents.updates}: an incident was created or re-scored by correlation. */
public record IncidentUpdateMessage(Long incidentId, Long orgId, String entityKey,
                                    String severity, int riskScore, boolean escalated) {
}
