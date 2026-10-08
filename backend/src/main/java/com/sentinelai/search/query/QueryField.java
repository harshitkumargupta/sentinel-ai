package com.sentinelai.search.query;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventOutcome;
import com.sentinelai.event.domain.EventType;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The whitelist of searchable event fields: query name (+ aliases), entity path and value kind.
 * Only these can appear in a query, so user input never reaches the query as a column name.
 */
public enum QueryField {
    SOURCE_IP("sourceIp", Kind.TEXT, "sourceIp", "ip", "src"),
    USERNAME("username", Kind.TEXT, "username", "user"),
    EVENT_TYPE("eventType", Kind.EVENT_TYPE, "eventType", "type"),
    OUTCOME("outcome", Kind.OUTCOME, "outcome"),
    SEVERITY("severity", Kind.SEVERITY, "severity"),
    RESOURCE("resource", Kind.TEXT, "resource", "path", "url"),
    GEO_COUNTRY("geoCountry", Kind.TEXT, "geoCountry", "country"),
    USER_AGENT("userAgent", Kind.TEXT, "userAgent"),
    ENTITY_KEY("entityKey", Kind.TEXT, "entityKey", "entity"),
    LOG_SOURCE("site.id", Kind.NUMBER, "sourceId", "logSource"),
    TIME("eventTimestamp", Kind.TIME, "eventTimestamp", "time");

    public enum Kind { TEXT, NUMBER, TIME, EVENT_TYPE, OUTCOME, SEVERITY }

    private final String path;
    private final Kind kind;
    private final List<String> names;

    QueryField(String path, Kind kind, String... names) {
        this.path = path;
        this.kind = kind;
        this.names = List.of(names);
    }

    public String path() {
        return path;
    }

    public Kind kind() {
        return kind;
    }

    public String displayName() {
        return names.get(0);
    }

    public static Optional<QueryField> byName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(f -> f.names.stream().anyMatch(a -> a.equalsIgnoreCase(n))).findFirst();
    }

    /** Allowed values for enum-kind fields (for validation and the UI's field help). */
    public List<String> allowedValues() {
        return switch (kind) {
            case EVENT_TYPE -> Arrays.stream(EventType.values()).map(Enum::name).toList();
            case OUTCOME -> Arrays.stream(EventOutcome.values()).map(Enum::name).toList();
            case SEVERITY -> Arrays.stream(Severity.values()).map(Enum::name).toList();
            default -> List.of();
        };
    }
}
