package com.sentinelai.event.domain;

/** Whether the action an event describes succeeded. Must match the ENUM in V28. */
public enum EventOutcome {
    SUCCESS,
    FAILURE,
    UNKNOWN;

    /** Default when a source doesn't say: login failures fail, login successes succeed. */
    public static EventOutcome defaultFor(EventType type) {
        if (type == EventType.FAILED_LOGIN) {
            return FAILURE;
        }
        if (type == EventType.LOGIN_SUCCESS) {
            return SUCCESS;
        }
        return UNKNOWN;
    }

    public static EventOutcome parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "SUCCESS", "SUCCEEDED", "OK", "ALLOW", "ALLOWED", "ACCEPT", "ACCEPTED", "PASS", "TRUE" -> SUCCESS;
            case "FAILURE", "FAIL", "FAILED", "DENY", "DENIED", "BLOCK", "BLOCKED", "REJECT", "REJECTED", "DROP", "FALSE" -> FAILURE;
            default -> UNKNOWN;
        };
    }
}
