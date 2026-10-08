package com.sentinelai.demo;

import com.sentinelai.ingestion.parse.LogFormat;
import com.sentinelai.site.domain.LogSourceType;

import java.util.List;
import java.util.Optional;

/** The bundled sample log files ({@code classpath:samples/}) the Demo Center can replay. */
public final class SampleDatasets {

    public record Dataset(String id, String label, String description, String resource, LogFormat format,
                          LogSourceType sourceType, String sourceName, List<String> expectedRules) {
    }

    public static final List<Dataset> ALL = List.of(
            new Dataset("nginx_access", "Web server access log",
                    "nginx combined log: normal browsing plus a scanner requesting common sensitive paths (404s).",
                    "samples/nginx-access.log", LogFormat.ACCESS_LOG, LogSourceType.WEB_SERVER,
                    "Sample: nginx web-01", List.of("HIGH_FREQUENCY_API")),
            new Dataset("auth_log", "Linux auth.log",
                    "sshd/sudo: staff logins, repeated failed root logins then a success, invalid-user sweep.",
                    "samples/auth.log", LogFormat.AUTH_LOG, LogSourceType.AUTH,
                    "Sample: web-01 auth.log", List.of("BRUTE_FORCE", "CREDENTIAL_STUFFING")),
            new Dataset("app_events", "Application events (JSON lines)",
                    "Application audit events including a viewer reaching an admin API.",
                    "samples/app-events.jsonl", LogFormat.JSON_LINES, LogSourceType.APPLICATION,
                    "Sample: orders app", List.of("ABNORMAL_ACCESS")),
            new Dataset("firewall_csv", "Firewall log (CSV)",
                    "Perimeter allow/deny records — searchable by outcome; no rule fires on these alone.",
                    "samples/firewall.csv", LogFormat.CSV, LogSourceType.FIREWALL,
                    "Sample: edge firewall", List.of()));

    private SampleDatasets() {
    }

    public static Optional<Dataset> find(String id) {
        return ALL.stream().filter(d -> d.id().equals(id)).findFirst();
    }
}
