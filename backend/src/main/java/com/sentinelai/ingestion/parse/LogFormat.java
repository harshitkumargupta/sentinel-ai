package com.sentinelai.ingestion.parse;

import com.sentinelai.site.domain.LogSourceType;

/** Supported raw log formats (QRadar: the DSM that understands a source's lines). */
public enum LogFormat {
    ACCESS_LOG,
    AUTH_LOG,
    JSON_LINES,
    CSV;

    /** The format a log source of this type sends unless told otherwise. */
    public static LogFormat defaultFor(LogSourceType type) {
        if (type == null) {
            return JSON_LINES;
        }
        return switch (type) {
            case WEB_SERVER -> ACCESS_LOG;
            case AUTH -> AUTH_LOG;
            case FIREWALL -> CSV;
            case APPLICATION, GENERIC -> JSON_LINES;
        };
    }
}
