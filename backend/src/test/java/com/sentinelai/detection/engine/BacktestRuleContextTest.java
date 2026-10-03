package com.sentinelai.detection.engine;

import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Validates the sliding-window counting (including expiry) that rules rely on. */
class BacktestRuleContextTest {

    private static final Organization ORG = Organization.builder().id(1L).name("o").build();

    private SecurityEvent failedLogin(long id, String user, Instant ts) {
        return SecurityEvent.builder().id(id).org(ORG).eventType(EventType.FAILED_LOGIN)
                .severity(Severity.LOW).username(user).honeytoken(false).eventTimestamp(ts).build();
    }

    @Test
    void countsWithinWindowAndExpiresOldEvents() {
        BacktestRuleContext ctx = new BacktestRuleContext();
        Instant t = Instant.parse("2026-02-01T12:00:00Z");

        ctx.advance(failedLogin(1, "bob", t));
        ctx.advance(failedLogin(2, "bob", t.plusSeconds(10)));
        ctx.advance(failedLogin(3, "bob", t.plusSeconds(20)));

        // All three are within a 300s window of the latest event.
        assertThat(ctx.countInWindow(1L, EventType.FAILED_LOGIN, GroupBy.USERNAME, "bob", 300))
                .isEqualTo(3);

        // A much later event: the earlier three fall outside the 300s window.
        ctx.advance(failedLogin(4, "bob", t.plusSeconds(1000)));
        assertThat(ctx.countInWindow(1L, EventType.FAILED_LOGIN, GroupBy.USERNAME, "bob", 300))
                .isEqualTo(1);
    }
}
