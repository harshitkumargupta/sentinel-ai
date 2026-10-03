package com.sentinelai.detection.engine;

import com.sentinelai.event.domain.SecurityEvent;

/** How a threshold rule groups events when counting toward its threshold. */
public enum GroupBy {
    USERNAME("username"),
    SOURCE_IP("sourceIp"),
    ENTITY_KEY("entityKey");

    private final String attribute;

    GroupBy(String attribute) {
        this.attribute = attribute;
    }

    /** JPA attribute name on {@link SecurityEvent} used for grouping/filtering. */
    public String attribute() {
        return attribute;
    }

    public String valueOf(SecurityEvent event) {
        return switch (this) {
            case USERNAME -> event.getUsername();
            case SOURCE_IP -> event.getSourceIp();
            case ENTITY_KEY -> event.getEntityKey();
        };
    }

    public static GroupBy fromString(String raw, GroupBy fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toUpperCase()) {
            case "USERNAME" -> USERNAME;
            case "SOURCE_IP", "SOURCEIP", "IP" -> SOURCE_IP;
            case "ENTITY_KEY", "ENTITYKEY" -> ENTITY_KEY;
            default -> fallback;
        };
    }
}
