package com.sentinelai.site.domain;

/** What a log source sends; selects the default parser (Phase 2) and is shown on the Log Sources page. */
public enum LogSourceType {
    GENERIC,
    WEB_SERVER,
    AUTH,
    FIREWALL,
    APPLICATION
}
