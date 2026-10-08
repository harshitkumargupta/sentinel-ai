package com.sentinelai.report;

/** The report kinds offered on the Reports page. Must match the ENUM in V33. */
public enum ReportType {
    INCIDENT_SUMMARY("Incident Summary"),
    TOP_ATTACKERS("Top Attackers"),
    ALERTS_BY_MITRE("Alerts by MITRE Technique"),
    RESPONSE_ACTIONS("Response Actions Taken");

    private final String title;

    ReportType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
