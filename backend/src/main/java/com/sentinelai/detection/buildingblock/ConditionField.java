package com.sentinelai.detection.buildingblock;

import com.sentinelai.event.domain.SecurityEvent;

import java.time.ZoneOffset;

/** Event attributes a building-block condition can test. */
public enum ConditionField {
    SOURCE_IP, USERNAME, EVENT_TYPE, OUTCOME, SEVERITY, RESOURCE, GEO_COUNTRY, USER_AGENT, HOUR;

    public String valueOf(SecurityEvent e) {
        return switch (this) {
            case SOURCE_IP -> e.getSourceIp();
            case USERNAME -> e.getUsername();
            case EVENT_TYPE -> e.getEventType() == null ? null : e.getEventType().name();
            case OUTCOME -> e.getOutcome() == null ? null : e.getOutcome().name();
            case SEVERITY -> e.getSeverity() == null ? null : e.getSeverity().name();
            case RESOURCE -> e.getResource();
            case GEO_COUNTRY -> e.getGeoCountry();
            case USER_AGENT -> e.getUserAgent();
            case HOUR -> e.getEventTimestamp() == null ? null
                    : String.valueOf(e.getEventTimestamp().atOffset(ZoneOffset.UTC).getHour());
        };
    }

    /** Accepts the JSON spelling ({@code sourceIp}) or the enum name ({@code SOURCE_IP}). */
    public static ConditionField parse(String raw) {
        String norm = raw == null ? "" : raw.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(java.util.Locale.ROOT);
        return ConditionField.valueOf(norm);
    }
}
