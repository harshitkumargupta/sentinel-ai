package com.sentinelai.ai.context;

import java.util.List;

/**
 * The structured, capped, sanitized view of an incident sent to the model. Never contains raw
 * unbounded logs, secrets, password hashes or API keys. All free-text fields are sanitized and
 * length-capped, and PII is redacted per config before this is serialized into the prompt.
 */
public record IncidentContext(
        Long incidentId,
        Long orgId,
        String severity,
        Integer riskScore,
        Entity entity,
        List<String> mitre,
        List<RiskFactor> riskFactors,
        List<EventSummary> events,
        List<TimelineItem> timeline,
        List<Long> eventIds) {

    public record Entity(String type, String value) {
    }

    public record RiskFactor(String name, int points, String reason) {
    }

    /** {@code host} is the endpoint name when the event's entity key is {@code host:<name>}. */
    public record EventSummary(Long id, String type, String severity, String sourceIp,
                               String username, String resource, String userAgent,
                               String geoCountry, String timestamp, String host) {

        public EventSummary(Long id, String type, String severity, String sourceIp, String username,
                            String resource, String userAgent, String geoCountry, String timestamp) {
            this(id, type, severity, sourceIp, username, resource, userAgent, geoCountry, timestamp, null);
        }
    }

    public record TimelineItem(String type, String actor, String at) {
    }
}
