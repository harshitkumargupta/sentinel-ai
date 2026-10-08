package com.sentinelai.ingestion.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Each parser: field mapping, skips vs. errors, and the bundled sample files end to end. */
class LogParsersTest {

    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    private static final LogLineParser.Context CTX = new LogLineParser.Context(NOW, List.of());

    private final ObjectMapper mapper = new ObjectMapper();
    private final LogParserService service = new LogParserService(List.of(
            new AccessLogParser(mapper), new AuthLogParser(mapper), new JsonLinesParser(mapper), new CsvParser(mapper)));

    @Test
    void accessLogCombinedAndCommon() {
        AccessLogParser p = new AccessLogParser(mapper);
        var rec = p.parse("203.0.113.5 - alice [08/Oct/2026:10:15:01 +0000] \"POST /login HTTP/1.1\" 401 512 \"-\" \"curl/8.0\"", CTX)
                .orElseThrow();
        assertThat(rec.sourceType()).isEqualTo("web");
        assertThat(rec.payload().get("sourceIp").asText()).isEqualTo("203.0.113.5");
        assertThat(rec.payload().get("username").asText()).isEqualTo("alice");
        assertThat(rec.payload().get("status").asInt()).isEqualTo(401);
        assertThat(rec.payload().get("path").asText()).isEqualTo("/login");
        assertThat(rec.payload().get("timestamp").asText()).isEqualTo("2026-10-08T10:15:01Z");
        assertThat(rec.payload().get("userAgent").asText()).isEqualTo("curl/8.0");
        assertThat(rec.payload().get("rawMessage").asText()).contains("POST /login");

        var common = p.parse("10.0.0.1 - - [08/Oct/2026:10:15:01 +0530] \"GET / HTTP/1.0\" 200 -", CTX).orElseThrow();
        assertThat(common.payload().has("username")).isFalse();
        assertThat(common.payload().get("timestamp").asText()).isEqualTo("2026-10-08T04:45:01Z");

        assertThatThrownBy(() -> p.parse("garbage", CTX)).isInstanceOf(LineParseException.class);
        assertThatThrownBy(() -> p.parse("1.1.1.1 - - [99/Foo/2026:10:15:01 +0000] \"GET / HTTP/1.1\" 200 1", CTX))
                .isInstanceOf(LineParseException.class);
    }

    @Test
    void authLogRecognizesFailuresSuccessesAndSkipsTheRest() {
        AuthLogParser p = new AuthLogParser(mapper);
        var failed = p.parse("Oct  8 10:00:01 web-01 sshd[12]: Failed password for invalid user admin from 45.33.1.2 port 22 ssh2", CTX)
                .orElseThrow();
        assertThat(failed.sourceType()).isEqualTo("auth");
        assertThat(failed.payload().get("username").asText()).isEqualTo("admin");
        assertThat(failed.payload().get("sourceIp").asText()).isEqualTo("45.33.1.2");
        assertThat(failed.payload().get("success").asBoolean()).isFalse();
        assertThat(failed.payload().get("timestamp").asText()).isEqualTo("2026-10-08T10:00:01Z");
        assertThat(failed.payload().get("host").asText()).isEqualTo("web-01");

        var ok = p.parse("Oct  8 10:00:05 web-01 sshd[13]: Accepted publickey for alice from 10.0.0.2 port 5 ssh2", CTX).orElseThrow();
        assertThat(ok.payload().get("success").asBoolean()).isTrue();

        var pam = p.parse("Oct  8 10:00:06 web-01 sshd[14]: pam_unix(sshd:auth): authentication failure; logname= uid=0 euid=0 tty=ssh ruser= rhost=198.51.100.3  user=root", CTX)
                .orElseThrow();
        assertThat(pam.payload().get("username").asText()).isEqualTo("root");
        assertThat(pam.payload().get("sourceIp").asText()).isEqualTo("198.51.100.3");

        var sudo = p.parse("Oct  8 10:00:07 web-01 sudo:    bob : 3 incorrect password attempts ; TTY=pts/0 ; COMMAND=/bin/sh", CTX)
                .orElseThrow();
        assertThat(sudo.payload().get("username").asText()).isEqualTo("bob");
        assertThat(sudo.payload().has("sourceIp")).isFalse();

        assertThat(p.parse("Oct  8 10:00:08 web-01 CRON[1]: pam_unix(cron:session): session opened for user root", CTX)).isEmpty();
        assertThatThrownBy(() -> p.parse("no syslog header here", CTX)).isInstanceOf(LineParseException.class);
    }

    @Test
    void syslogYearRollsBackForDecemberLinesReadInJanuary() {
        Instant jan = Instant.parse("2027-01-02T00:00:00Z");
        assertThat(AuthLogParser.timestamp("Dec 31 23:59:00", jan)).isEqualTo(Instant.parse("2026-12-31T23:59:00Z"));
    }

    @Test
    void jsonLinesAndCsv() {
        JsonLinesParser json = new JsonLinesParser(mapper);
        var rec = json.parse("{\"time\":\"2026-10-08T10:00:00Z\",\"eventType\":\"OTHER\",\"username\":\"u\"}", CTX).orElseThrow();
        assertThat(rec.payload().get("eventTimestamp").asText()).isEqualTo("2026-10-08T10:00:00Z");
        assertThatThrownBy(() -> json.parse("{broken", CTX)).isInstanceOf(LineParseException.class);
        assertThatThrownBy(() -> json.parse("[1,2]", CTX)).isInstanceOf(LineParseException.class);

        CsvParser csv = new CsvParser(mapper);
        var ctx = new LogLineParser.Context(NOW, List.of("time", "src_ip", "user", "action", "note"));
        var row = csv.parse("2026-10-08T10:00:00Z,10.0.0.9,\"smith, j\",deny,\"say \"\"hi\"\"\"", ctx).orElseThrow();
        assertThat(row.payload().get("eventTimestamp").asText()).isEqualTo("2026-10-08T10:00:00Z");
        assertThat(row.payload().get("sourceIp").asText()).isEqualTo("10.0.0.9");
        assertThat(row.payload().get("username").asText()).isEqualTo("smith, j");
        assertThat(row.payload().get("outcome").asText()).isEqualTo("deny");
        assertThat(row.payload().get("note").asText()).isEqualTo("say \"hi\"");
        assertThatThrownBy(() -> csv.parse("only,two", ctx)).isInstanceOf(LineParseException.class);
        assertThatThrownBy(() -> csv.parse("a,\"unterminated,b,c,d", ctx)).isInstanceOf(LineParseException.class);
    }

    @Test
    void bundledSamplesParseWithTheirIntentionalErrors() throws Exception {
        ParseResult nginx = service.parse(LogFormat.ACCESS_LOG, sample("nginx-access.log"), NOW);
        assertThat(nginx.records()).hasSize(180);
        assertThat(nginx.errors()).isEqualTo(1);
        assertThat(nginx.errorSamples().get(0)).contains("not a common/combined access-log line");

        ParseResult auth = service.parse(LogFormat.AUTH_LOG, sample("auth.log"), NOW);
        assertThat(auth.errors()).isEqualTo(1);
        assertThat(auth.skipped()).isEqualTo(11); // pam session + cron lines
        assertThat(auth.records()).hasSize(10 + 14 + 1 + 6 + 1);

        ParseResult app = service.parse(LogFormat.JSON_LINES, sample("app-events.jsonl"), NOW);
        assertThat(app.records()).hasSize(21);
        assertThat(app.errors()).isEqualTo(1);

        ParseResult fw = service.parse(LogFormat.CSV, sample("firewall.csv"), NOW);
        assertThat(fw.records()).hasSize(40);
        assertThat(fw.errors()).isZero();
    }

    private static List<String> sample(String name) throws Exception {
        try (var in = new ClassPathResource("samples/" + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }
}
