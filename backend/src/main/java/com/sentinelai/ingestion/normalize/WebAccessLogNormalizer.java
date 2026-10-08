package com.sentinelai.ingestion.normalize;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventOutcome;
import com.sentinelai.event.domain.EventType;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

import static com.sentinelai.ingestion.normalize.NormalizerSupport.text;
import static com.sentinelai.ingestion.normalize.NormalizerSupport.timestamp;

/**
 * Maps one web request — an access-log line or an event from the app middleware snippets:
 * {@code {sourceIp, method, path, status, userAgent, username, timestamp}}. Classification:
 * <ul>
 *   <li>SQL-injection markers in the path/query (quote, {@code UNION SELECT}, {@code OR 1=1}, …) → SQL_INJECTION</li>
 *   <li>POST to a login endpoint: 401/403 → FAILED_LOGIN, otherwise below 400 → LOGIN_SUCCESS</li>
 *   <li>unauthenticated request for a commonly probed path ({@code /.env}, {@code /wp-login.php}, {@code /admin}, …) → ABNORMAL_ACCESS</li>
 *   <li>any other error response (≥ 400) → API_ABUSE (feeds the high-frequency rule); else OTHER</li>
 * </ul>
 * The entity key is the client IP so one visitor's requests correlate into one offense.
 */
@Component
public class WebAccessLogNormalizer implements EventNormalizer {

    static final Pattern SQLI = Pattern.compile(
            "(?i)('|%27|\\bunion\\b.{0,40}\\bselect\\b|\\bor\\b\\s*'?\\d+'?\\s*=\\s*'?\\d+|--\\s|%2d%2d|;\\s*drop\\b|\\bsleep\\s*\\()");
    static final Pattern LOGIN = Pattern.compile(
            "(?i)^/(?:[\\w-]+/)*(login|log-in|signin|sign-in|auth|authenticate|session|sessions|wp-login\\.php)(?:[/?.].*)?$");
    static final Pattern PROBE = Pattern.compile(
            "(?i)^/(\\.env|\\.git|\\.aws|\\.ssh|wp-login\\.php|wp-admin|xmlrpc\\.php|phpmyadmin|pma|admin|administrator|"
                    + "config\\.php|backup|backup\\.zip|db\\.sql|server-status|actuator)(?:[/?.].*)?$");

    @Override
    public String sourceType() {
        return "web";
    }

    @Override
    public NormalizedEvent normalize(JsonNode raw) {
        int status = raw.has("status") && raw.get("status").canConvertToInt() ? raw.get("status").asInt() : 200;
        String method = text(raw, "method");
        String path = text(raw, "path");
        String username = text(raw, "username");
        String ip = text(raw, "sourceIp");

        EventType type;
        Severity severity;
        EventOutcome outcome = status >= 400 ? EventOutcome.FAILURE : EventOutcome.SUCCESS;
        String decoded = path == null ? "" : path.replace('+', ' ');
        if (path != null && SQLI.matcher(decoded).find()) {
            type = EventType.SQL_INJECTION;
            severity = Severity.HIGH;
        } else if (path != null && "POST".equalsIgnoreCase(method) && LOGIN.matcher(path).matches()) {
            boolean failed = status == 401 || status == 403;
            type = failed ? EventType.FAILED_LOGIN : status < 400 ? EventType.LOGIN_SUCCESS : EventType.API_ABUSE;
            severity = Severity.LOW;
            outcome = failed || status >= 400 ? EventOutcome.FAILURE : EventOutcome.SUCCESS;
        } else if (path != null && username == null && PROBE.matcher(path).matches()) {
            type = EventType.ABNORMAL_ACCESS;
            severity = Severity.MEDIUM;
        } else {
            type = status >= 400 ? EventType.API_ABUSE : EventType.OTHER;
            severity = status >= 500 ? Severity.MEDIUM : Severity.LOW;
        }
        return NormalizedEvent.builder()
                .eventType(type)
                .severity(severity)
                .outcome(outcome)
                .sourceIp(ip)
                .username(username)
                .userAgent(cut(text(raw, "userAgent"), 512))
                .resource(cut(path, 255))
                .entityKey(ip != null ? "ip:" + ip : null)
                .eventTimestamp(timestamp(raw, "timestamp"))
                .clientEventId(text(raw, "clientEventId"))
                .rawPayload(raw.toString())
                .build();
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
