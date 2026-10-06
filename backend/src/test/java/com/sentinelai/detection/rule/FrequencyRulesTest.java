package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.engine.AlertDraft;
import com.sentinelai.detection.support.FakeRuleContext;
import com.sentinelai.detection.support.RuleTestFixtures;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Hit / near-miss for the count-based rules (window logic is covered in BacktestRuleContextTest). */
class FrequencyRulesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void bruteForceFiresAtThresholdAndNotBelow() {
        BruteForceRule rule = new BruteForceRule(mapper);
        var def = RuleTestFixtures.rule("BRUTE_FORCE",
                "{\"threshold\":10,\"windowSeconds\":300,\"groupBy\":\"username\"}", Severity.HIGH, "T1110");
        SecurityEvent event = RuleTestFixtures.event(EventType.FAILED_LOGIN).username("victim").build();

        FakeRuleContext ctx = new FakeRuleContext();
        ctx.count = 9; // near miss
        assertThat(rule.evaluate(event, def, ctx)).isEmpty();

        ctx.count = 10; // hit
        Optional<AlertDraft> draft = rule.evaluate(event, def, ctx);
        assertThat(draft).isPresent();
        assertThat(draft.get().severity()).isEqualTo(Severity.HIGH);
        assertThat(draft.get().entityKey()).isEqualTo("USERNAME:victim");
    }

    @Test
    void bruteForceIgnoresOtherEventTypes() {
        BruteForceRule rule = new BruteForceRule(mapper);
        var def = RuleTestFixtures.rule("BRUTE_FORCE", "{\"threshold\":1}", Severity.HIGH, "T1110");
        SecurityEvent event = RuleTestFixtures.event(EventType.API_ABUSE).username("victim").build();
        FakeRuleContext ctx = new FakeRuleContext();
        ctx.count = 100;
        assertThat(rule.evaluate(event, def, ctx)).isEmpty();
    }

    @Test
    void credentialStuffingFiresOnDistinctUsers() {
        CredentialStuffingRule rule = new CredentialStuffingRule(mapper);
        var def = RuleTestFixtures.rule("CREDENTIAL_STUFFING",
                "{\"distinctUsers\":5,\"windowSeconds\":300}", Severity.HIGH, "T1110.004");
        SecurityEvent event = RuleTestFixtures.event(EventType.FAILED_LOGIN).sourceIp("1.2.3.4").build();

        FakeRuleContext ctx = new FakeRuleContext();
        ctx.distinctUsers = 4;
        assertThat(rule.evaluate(event, def, ctx)).isEmpty();

        ctx.distinctUsers = 5;
        assertThat(rule.evaluate(event, def, ctx)).isPresent();
    }

    @Test
    void highFrequencyApiFiresAboveThreshold() {
        HighFrequencyApiRule rule = new HighFrequencyApiRule(mapper);
        var def = RuleTestFixtures.rule("HIGH_FREQUENCY_API",
                "{\"threshold\":100,\"windowSeconds\":60,\"groupBy\":\"sourceIp\"}", Severity.MEDIUM, "T1499");
        SecurityEvent event = RuleTestFixtures.event(EventType.API_ABUSE).sourceIp("9.9.9.9").build();

        FakeRuleContext ctx = new FakeRuleContext();
        ctx.count = 150;
        assertThat(rule.evaluate(event, def, ctx)).isPresent();
        ctx.count = 50;
        assertThat(rule.evaluate(event, def, ctx)).isEmpty();
    }
}
