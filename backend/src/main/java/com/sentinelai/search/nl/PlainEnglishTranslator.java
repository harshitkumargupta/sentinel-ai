package com.sentinelai.search.nl;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline, rule-based "ask in plain English" → event-search query. Recognizes time ranges (last
 * hour / N minutes|hours|days, today, yesterday, this|last week), failed / successful logins, IPv4s,
 * users, event-type phrases, severity, countries, and "top N IPs|users|countries". Everything it
 * understood is listed so the user can check, and the generated query stays editable. Times are UTC.
 */
@Component
public class PlainEnglishTranslator {

    public record Top(String field, int n) {
    }

    public record Translation(boolean understood, String query, Top top, List<String> understoodParts, String message) {
    }

    private static final Pattern IP = Pattern.compile("\\b((?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3})\\b");
    private static final Pattern LAST_N = Pattern.compile("\\b(?:last|past)\\s+(\\d{1,3})\\s+(minute|minutes|min|mins|hour|hours|hr|hrs|day|days|week|weeks)\\b");
    private static final Pattern USER = Pattern.compile("\\b(?:user|username|account|for user|by user)\\s+['\"]?([a-z0-9._@\\-]{2,100})['\"]?");
    private static final Pattern TOP = Pattern.compile("\\btop\\s+(\\d{1,2})\\s+(ips?|ip addresses|sources?|source ips?|attackers?|users?|accounts?|countries|country|event types?|types?)\\b");
    private static final Map<String, String> TYPES = new LinkedHashMap<>();
    private static final Map<String, String> COUNTRIES = Map.ofEntries(
            Map.entry("russia", "RU"), Map.entry("china", "CN"), Map.entry("united states", "US"), Map.entry("usa", "US"),
            Map.entry("germany", "DE"), Map.entry("netherlands", "NL"), Map.entry("brazil", "BR"), Map.entry("india", "IN"),
            Map.entry("iran", "IR"), Map.entry("ukraine", "UA"), Map.entry("nigeria", "NG"), Map.entry("vietnam", "VN"),
            Map.entry("korea", "KR"), Map.entry("united kingdom", "GB"), Map.entry("uk", "GB"), Map.entry("france", "FR"));

    static {
        TYPES.put("brute force", "BRUTE_FORCE");
        TYPES.put("port scan", "PORT_SCAN");
        TYPES.put("sql injection", "SQL_INJECTION");
        TYPES.put("sqli", "SQL_INJECTION");
        TYPES.put("malware", "MALWARE_DETECTED");
        TYPES.put("privilege escalation", "PRIVILEGE_ESCALATION");
        TYPES.put("exfiltration", "DATA_TRANSFER");
        TYPES.put("data transfer", "DATA_TRANSFER");
        TYPES.put("phishing", "PHISHING_CLICK");
        TYPES.put("ddos", "NETWORK_FLOOD");
        TYPES.put("flood", "NETWORK_FLOOD");
        TYPES.put("api abuse", "API_ABUSE");
        TYPES.put("honeytoken", "HONEYTOKEN_ACCESS");
        TYPES.put("decoy", "HONEYTOKEN_ACCESS");
        TYPES.put("suspicious login", "SUSPICIOUS_LOGIN");
        TYPES.put("impossible travel", "SUSPICIOUS_LOGIN");
        TYPES.put("abnormal access", "ABNORMAL_ACCESS");
        TYPES.put("prompt injection", "PROMPT_INJECTION");
    }

    private final Clock clock;

    public PlainEnglishTranslator(Clock clock) {
        this.clock = clock;
    }

    public Translation translate(String text) {
        if (text == null || text.isBlank()) {
            return new Translation(false, null, null, List.of(), "Type a question, e.g. “failed logins from 45.33.12.7 in the last hour”.");
        }
        String t = " " + text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim() + " ";
        List<String> parts = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        Instant now = clock.instant();

        // --- time range ---
        Instant from = null;
        Instant to = null;
        Matcher lastN = LAST_N.matcher(t);
        if (lastN.find()) {
            int n = Integer.parseInt(lastN.group(1));
            String u = lastN.group(2);
            Duration d = u.startsWith("min") ? Duration.ofMinutes(n) : u.startsWith("h") ? Duration.ofHours(n)
                    : u.startsWith("d") ? Duration.ofDays(n) : Duration.ofDays(7L * n);
            from = now.minus(d);
            parts.add("time: last " + n + " " + u);
        } else if (t.contains(" last hour ") || t.contains(" past hour ")) {
            from = now.minus(1, ChronoUnit.HOURS);
            parts.add("time: last hour");
        } else if (t.contains(" yesterday ")) {
            LocalDate y = now.atOffset(ZoneOffset.UTC).toLocalDate().minusDays(1);
            from = y.atStartOfDay().toInstant(ZoneOffset.UTC);
            to = y.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1);
            parts.add("time: yesterday (UTC)");
        } else if (t.contains(" today ")) {
            from = now.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
            parts.add("time: today (UTC)");
        } else if (t.contains(" this week ") || t.contains(" last week ") || t.contains(" past week ")) {
            from = now.minus(7, ChronoUnit.DAYS);
            parts.add("time: last 7 days");
        } else if (t.contains(" last 24 hours ") || t.contains(" past day ") || t.contains(" last day ")) {
            from = now.minus(24, ChronoUnit.HOURS);
            parts.add("time: last 24 hours");
        }
        if (from != null) {
            conds.add("time >= '" + from + "'");
        }
        if (to != null) {
            conds.add("time <= '" + to + "'");
        }

        // --- logins / outcome ---
        boolean login = t.matches(".*\\blog ?ins?\\b.*|.*\\bsign ?ins?\\b.*|.*\\bauth.*");
        boolean failed = t.matches(".*\\b(failed|failures?|unsuccessful|bad password|wrong password|denied|blocked)\\b.*");
        boolean success = t.matches(".*\\b(successful|succeeded|success|accepted)\\b.*");
        if (failed && login) {
            conds.add("type = 'FAILED_LOGIN'");
            parts.add("failed logins");
        } else if (success && login) {
            conds.add("type IN ('LOGIN_SUCCESS', 'SUSPICIOUS_LOGIN')");
            conds.add("outcome = 'SUCCESS'");
            parts.add("successful logins");
        } else if (failed) {
            conds.add("outcome = 'FAILURE'");
            parts.add("failed actions");
        } else if (success) {
            conds.add("outcome = 'SUCCESS'");
            parts.add("successful actions");
        } else if (login) {
            conds.add("type IN ('FAILED_LOGIN', 'LOGIN_SUCCESS', 'SUSPICIOUS_LOGIN')");
            parts.add("logins");
        }

        // --- event types ---
        for (var e : TYPES.entrySet()) {
            if (t.contains(" " + e.getKey())) {
                conds.add("type = '" + e.getValue() + "'");
                parts.add("event type " + e.getValue());
                break;
            }
        }

        // --- ip / user / country / severity ---
        Matcher ip = IP.matcher(t);
        List<String> ips = new ArrayList<>();
        while (ip.find()) {
            ips.add(ip.group(1));
        }
        if (ips.size() == 1) {
            conds.add("ip = '" + ips.get(0) + "'");
            parts.add("source IP " + ips.get(0));
        } else if (ips.size() > 1) {
            conds.add("ip IN (" + String.join(", ", ips.stream().map(x -> "'" + x + "'").toList()) + ")");
            parts.add("source IPs " + String.join(", ", ips));
        }
        Matcher user = USER.matcher(t);
        if (user.find() && !List.of("logins", "login", "accounts", "users").contains(user.group(1))) {
            conds.add("user = '" + user.group(1) + "'");
            parts.add("user " + user.group(1));
        }
        for (var c : COUNTRIES.entrySet()) {
            if (t.contains(" " + c.getKey() + " ")) {
                conds.add("country = '" + c.getValue() + "'");
                parts.add("country " + c.getValue());
                break;
            }
        }
        for (String sev : List.of("critical", "high", "medium", "low")) {
            if (t.contains(" " + sev + " severity ") || t.contains(" " + sev + "-severity ") || t.contains(" severity " + sev + " ")
                    || (t.contains(" " + sev + " ") && t.contains(" alert"))) {
                conds.add("severity = '" + sev.toUpperCase(Locale.ROOT) + "'");
                parts.add("severity " + sev.toUpperCase(Locale.ROOT));
                break;
            }
        }

        // --- top N ---
        Top top = null;
        Matcher tm = TOP.matcher(t);
        if (tm.find()) {
            int n = Math.max(1, Math.min(50, Integer.parseInt(tm.group(1))));
            String w = tm.group(2);
            String field = w.startsWith("user") || w.startsWith("account") ? "username"
                    : w.startsWith("countr") ? "geoCountry" : w.contains("type") ? "eventType" : "sourceIp";
            top = new Top(field, n);
            parts.add("top " + n + " by " + field);
        }

        if (parts.isEmpty()) {
            return new Translation(false, null, null, List.of(),
                    "Sorry, I couldn't understand that. Try a time range (last hour, yesterday), failed or successful logins, "
                            + "an IP, “user alice”, an attack like “brute force”, a country, or “top 5 IPs”.");
        }
        String query = String.join(" AND ", conds);
        return new Translation(true, query.isEmpty() ? null : query, top, parts, "Understood: " + String.join("; ", parts));
    }
}
