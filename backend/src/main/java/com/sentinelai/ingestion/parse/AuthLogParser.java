package com.sentinelai.ingestion.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Linux {@code auth.log} (syslog, sshd/sudo/PAM). Recognized authentication lines become
 * {@code auth} records (success/failure, user, source IP); other syslog lines are skipped; lines
 * without a syslog header are errors. Syslog has no year or zone: the current UTC year is assumed,
 * rolled back a year if that would put the line in the future.
 */
@Component
@RequiredArgsConstructor
public class AuthLogParser implements LogLineParser {

    private static final Pattern SYSLOG = Pattern.compile(
            "^([A-Z][a-z]{2}\\s+\\d{1,2} \\d{2}:\\d{2}:\\d{2}) (\\S+) ([\\w.\\-/]+)(?:\\[\\d+])?: (.*)$");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy MMM d HH:mm:ss", Locale.ENGLISH);

    private record Rule(Pattern pattern, boolean success) {
    }

    /** Group 1 = user, group 2 = source IP (when present). */
    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("^Failed (?:password|publickey) for (?:invalid user )?(\\S+) from (\\S+)"), false),
            new Rule(Pattern.compile("^Invalid user (\\S+) from (\\S+)"), false),
            new Rule(Pattern.compile("^Accepted (?:password|publickey|keyboard-interactive/pam) for (\\S+) from (\\S+)"), true),
            new Rule(Pattern.compile("authentication failure;.*?rhost=(\\S*)\\s+user=(\\S+)"), false),
            new Rule(Pattern.compile("^\\s*(\\S+) : .*incorrect password attempts"), false));

    private final ObjectMapper objectMapper;

    @Override
    public LogFormat format() {
        return LogFormat.AUTH_LOG;
    }

    @Override
    public Optional<ParsedRecord> parse(String line, Context ctx) {
        Matcher h = SYSLOG.matcher(line);
        if (!h.matches()) {
            throw new LineParseException("not a syslog auth.log line");
        }
        String message = h.group(4);
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(message);
            if (m.find()) {
                ObjectNode p = objectMapper.createObjectNode();
                boolean pamFailure = rule.pattern().pattern().startsWith("authentication failure");
                String user = pamFailure ? m.group(2) : m.group(1);
                String ip = pamFailure ? m.group(1) : (m.groupCount() >= 2 ? m.group(2) : null);
                p.put("username", user);
                if (ip != null && !ip.isBlank()) {
                    p.put("sourceIp", ip);
                }
                p.put("success", rule.success());
                p.put("timestamp", timestamp(h.group(1), ctx.now()).toString());
                p.put("host", h.group(2));
                p.put("program", h.group(3));
                p.put("rawMessage", line);
                return Optional.of(new ParsedRecord("auth", p));
            }
        }
        return Optional.empty(); // valid syslog line, not an authentication event
    }

    static Instant timestamp(String syslogTime, Instant now) {
        int year = now.atOffset(ZoneOffset.UTC).getYear();
        try {
            String normalized = syslogTime.replaceAll("\\s+", " ");
            Instant t = LocalDateTime.parse(year + " " + normalized, TIME).toInstant(ZoneOffset.UTC);
            return t.isAfter(now.plusSeconds(86_400))
                    ? LocalDateTime.parse((year - 1) + " " + normalized, TIME).toInstant(ZoneOffset.UTC)
                    : t;
        } catch (DateTimeParseException e) {
            throw new LineParseException("bad syslog timestamp: " + syslogTime);
        }
    }
}
