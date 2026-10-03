package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.support.FakeRuleContext;
import com.sentinelai.detection.support.RuleTestFixtures;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StatefulRulesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void suspiciousLoginFiresOnNewCountry() {
        SuspiciousLoginRule rule = new SuspiciousLoginRule(mapper);
        var def = RuleTestFixtures.rule("SUSPICIOUS_LOGIN",
                "{\"oddHourStart\":0,\"oddHourEnd\":5}", Severity.MEDIUM, "T1078");
        SecurityEvent event = RuleTestFixtures.event(EventType.SUSPICIOUS_LOGIN)
                .username("bob").geoCountry("RU")
                .eventTimestamp(Instant.parse("2026-02-01T12:00:00Z")).build();

        FakeRuleContext ctx = new FakeRuleContext();
        ctx.previous = RuleTestFixtures.event(EventType.SUSPICIOUS_LOGIN).id(50L).username("bob")
                .geoCountry("US").build();
        assertThat(rule.evaluate(event, def, ctx)).isPresent();
    }

    @Test
    void suspiciousLoginFiresOnOddHourEvenSameCountry() {
        SuspiciousLoginRule rule = new SuspiciousLoginRule(mapper);
        var def = RuleTestFixtures.rule("SUSPICIOUS_LOGIN",
                "{\"oddHourStart\":0,\"oddHourEnd\":5}", Severity.MEDIUM, "T1078");
        SecurityEvent event = RuleTestFixtures.event(EventType.SUSPICIOUS_LOGIN)
                .username("bob").geoCountry("US")
                .eventTimestamp(Instant.parse("2026-02-01T03:00:00Z")).build();
        FakeRuleContext ctx = new FakeRuleContext();
        ctx.previous = RuleTestFixtures.event(EventType.SUSPICIOUS_LOGIN).id(50L).username("bob")
                .geoCountry("US").build();
        assertThat(rule.evaluate(event, def, ctx)).isPresent();
    }

    @Test
    void suspiciousLoginDoesNotFireForNormalLogin() {
        SuspiciousLoginRule rule = new SuspiciousLoginRule(mapper);
        var def = RuleTestFixtures.rule("SUSPICIOUS_LOGIN",
                "{\"oddHourStart\":0,\"oddHourEnd\":5}", Severity.MEDIUM, "T1078");
        SecurityEvent event = RuleTestFixtures.event(EventType.SUSPICIOUS_LOGIN)
                .username("bob").geoCountry("US")
                .eventTimestamp(Instant.parse("2026-02-01T12:00:00Z")).build();
        FakeRuleContext ctx = new FakeRuleContext();
        ctx.previous = RuleTestFixtures.event(EventType.SUSPICIOUS_LOGIN).id(50L).username("bob")
                .geoCountry("US").build();
        assertThat(rule.evaluate(event, def, ctx)).isEmpty();
    }

    @Test
    void impossibleTravelFiresOnFastCountryChange() {
        ImpossibleTravelRule rule = new ImpossibleTravelRule(mapper);
        var def = RuleTestFixtures.rule("IMPOSSIBLE_TRAVEL",
                "{\"minSecondsBetweenCountries\":3600}", Severity.HIGH, "T1078");
        SecurityEvent event = RuleTestFixtures.event(EventType.SUSPICIOUS_LOGIN)
                .username("bob").geoCountry("RU")
                .eventTimestamp(Instant.parse("2026-02-01T12:30:00Z")).build();
        FakeRuleContext ctx = new FakeRuleContext();
        ctx.previous = RuleTestFixtures.event(EventType.SUSPICIOUS_LOGIN).id(50L).username("bob")
                .geoCountry("US").eventTimestamp(Instant.parse("2026-02-01T12:00:00Z")).build();
        assertThat(rule.evaluate(event, def, ctx)).isPresent();
    }

    @Test
    void impossibleTravelDoesNotFireWhenSlowOrSameCountry() {
        ImpossibleTravelRule rule = new ImpossibleTravelRule(mapper);
        var def = RuleTestFixtures.rule("IMPOSSIBLE_TRAVEL",
                "{\"minSecondsBetweenCountries\":3600}", Severity.HIGH, "T1078");
        SecurityEvent event = RuleTestFixtures.event(EventType.SUSPICIOUS_LOGIN)
                .username("bob").geoCountry("RU")
                .eventTimestamp(Instant.parse("2026-02-01T20:00:00Z")).build(); // 8h later -> plausible
        FakeRuleContext ctx = new FakeRuleContext();
        ctx.previous = RuleTestFixtures.event(EventType.SUSPICIOUS_LOGIN).id(50L).username("bob")
                .geoCountry("US").eventTimestamp(Instant.parse("2026-02-01T12:00:00Z")).build();
        assertThat(rule.evaluate(event, def, ctx)).isEmpty();
    }

    @Test
    void abnormalAccessFiresOnlyForAbnormalAccessEvents() {
        AbnormalAccessRule rule = new AbnormalAccessRule(mapper);
        var def = RuleTestFixtures.rule("ABNORMAL_ACCESS", "{}", Severity.HIGH, "T1548");
        FakeRuleContext ctx = new FakeRuleContext();

        SecurityEvent hit = RuleTestFixtures.event(EventType.ABNORMAL_ACCESS)
                .username("viewer").resource("/api/admin/users").build();
        assertThat(rule.evaluate(hit, def, ctx)).isPresent();

        SecurityEvent miss = RuleTestFixtures.event(EventType.OTHER).username("viewer").build();
        assertThat(rule.evaluate(miss, def, ctx)).isEmpty();
    }
}
