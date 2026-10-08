package com.sentinelai.search;

import com.sentinelai.event.domain.SecurityEvent;

import java.util.List;

/**
 * RFC 4180 CSV for event exports, with spreadsheet formula-injection protection: a cell starting with
 * {@code = + - @}, tab or CR is prefixed with an apostrophe so Excel/Sheets treat it as text.
 */
public final class CsvWriter {

    private static final String HEADER =
            "id,time,eventType,outcome,severity,sourceIp,username,resource,geoCountry,userAgent,sourceId,entityKey";

    private CsvWriter() {
    }

    public static String events(List<SecurityEvent> rows) {
        StringBuilder sb = new StringBuilder(HEADER).append("\r\n");
        for (SecurityEvent e : rows) {
            sb.append(String.join(",",
                    cell(e.getId()), cell(e.getEventTimestamp()), cell(e.getEventType()), cell(e.getOutcome()),
                    cell(e.getSeverity()), cell(e.getSourceIp()), cell(e.getUsername()), cell(e.getResource()),
                    cell(e.getGeoCountry()), cell(e.getUserAgent()),
                    cell(e.getSite() == null ? null : e.getSite().getId()), cell(e.getEntityKey())))
                    .append("\r\n");
        }
        return sb.toString();
    }

    public static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
