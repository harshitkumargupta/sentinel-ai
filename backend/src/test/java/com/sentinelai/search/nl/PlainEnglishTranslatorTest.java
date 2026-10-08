package com.sentinelai.search.nl;

import com.sentinelai.search.query.QueryParser;
import com.sentinelai.search.query.QuerySpecification;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The 10 UI example phrases translate to valid queries; unknown phrases say so. */
class PlainEnglishTranslatorTest {

    private static final Instant NOW = Instant.parse("2026-10-08T12:30:00Z");
    private final PlainEnglishTranslator t = new PlainEnglishTranslator(Clock.fixed(NOW, ZoneOffset.UTC));

    /** Mirrors the example chips on the Event Search page. */
    static final List<String> EXAMPLES = List.of(
            "failed logins in the last hour",
            "successful logins yesterday",
            "failed logins from 45.33.12.7",
            "everything for user alice today",
            "brute force in the last 24 hours",
            "top 5 IPs with failed logins this week",
            "port scan from 185.220.101.4",
            "logins from Russia in the last 7 days",
            "high severity alerts last 2 hours",
            "top 10 users in the last 30 days");

    @Test
    void examplesProduceParseableQueries() {
        for (String ex : EXAMPLES) {
            var r = t.translate(ex);
            assertThat(r.understood()).as(ex).isTrue();
            assertThat(r.understoodParts()).as(ex).isNotEmpty();
            if (r.query() != null) {
                QuerySpecification.of(QueryParser.parse(r.query())); // throws if invalid
            }
        }
    }

    @Test
    void specificTranslations() {
        assertThat(t.translate("failed logins in the last hour").query())
                .isEqualTo("time >= '2026-10-08T11:30:00Z' AND type = 'FAILED_LOGIN'");
        assertThat(t.translate("successful logins yesterday").query())
                .isEqualTo("time >= '2026-10-07T00:00:00Z' AND time <= '2026-10-07T23:59:59.999Z' "
                        + "AND type IN ('LOGIN_SUCCESS', 'SUSPICIOUS_LOGIN') AND outcome = 'SUCCESS'");
        assertThat(t.translate("failed logins from 45.33.12.7").query()).isEqualTo("type = 'FAILED_LOGIN' AND ip = '45.33.12.7'");
        assertThat(t.translate("logins from Russia in the last 7 days").query()).contains("country = 'RU'");
        var top = t.translate("top 5 IPs with failed logins this week");
        assertThat(top.top().field()).isEqualTo("sourceIp");
        assertThat(top.top().n()).isEqualTo(5);
        assertThat(t.translate("top 10 users in the last 30 days").top().field()).isEqualTo("username");
        assertThat(t.translate("everything for user alice today").query()).contains("user = 'alice'");
    }

    @Test
    void notUnderstoodIsExplained() {
        var r = t.translate("what is the weather like");
        assertThat(r.understood()).isFalse();
        assertThat(r.message()).startsWith("Sorry, I couldn't understand that");
        assertThat(t.translate("  ").understood()).isFalse();
    }
}
