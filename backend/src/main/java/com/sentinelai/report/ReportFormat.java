package com.sentinelai.report;

/** Output format; must match the ENUM in V33. */
public enum ReportFormat {
    PDF("application/pdf", "pdf"),
    CSV("text/csv", "csv");

    private final String mediaType;
    private final String extension;

    ReportFormat(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return extension;
    }
}
