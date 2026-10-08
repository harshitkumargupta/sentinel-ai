package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.support.FakeRuleContext;
import com.sentinelai.detection.support.RuleTestFixtures;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Threshold-crossing behavior of the shared count rule, and the exfiltration size filter. */
class EventCountRulesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void portScanFiresAtThresholdAndItsMultiplesOnly() {
        PortScanRule rule = new PortScanRule(mapper);
        var def = RuleTestFixtures.rule("PORT_SCAN",
                "{\"threshold\":20,\"windowSeconds\":120,\"groupBy\":\"sourceIp\"}", Severity.MEDIUM, "T1046");
        SecurityEvent event = RuleTestFixtures.event(EventType.PORT_SCAN).sourceIp("198.51.100.7").build();
        FakeRuleContext ctx = new FakeRuleContext();

        ctx.count = 19;
        assertThat(rule.evaluate(event, def, ctx)).isEmpty();
        ctx.count = 20;
        assertThat(rule.evaluate(event, def, ctx)).isPresent()
                .get().satisfies(d -> {
                    assertThat(d.mitreTechnique()).isEqualTo("T1046");
                    assertThat(d.entityKey()).isEqualTo("SOURCE_IP:198.51.100.7");
                });
        ctx.count = 21; // past the threshold but not a multiple: no duplicate alert
        assertThat(rule.evaluate(event, def, ctx)).isEmpty();
        ctx.count = 40;
        assertThat(rule.evaluate(event, def, ctx)).isPresent();
    }

    @Test
    void countRuleIgnoresOtherTypesAndMissingGroupValue() {
        SqlInjectionRule rule = new SqlInjectionRule(mapper);
        var def = RuleTestFixtures.rule("SQL_INJECTION", "{\"threshold\":1}", Severity.HIGH, "T1190");
        FakeRuleContext ctx = new FakeRuleContext();
        ctx.count = 1;
        assertThat(rule.evaluate(RuleTestFixtures.event(EventType.FAILED_LOGIN).sourceIp("1.2.3.4").build(), def, ctx))
                .isEmpty();
        assertThat(rule.evaluate(RuleTestFixtures.event(EventType.SQL_INJECTION).build(), def, ctx))
                .isEmpty(); // no source IP to group by
    }

    @Test
    void exfiltrationRequiresMinimumBytes() {
        DataExfiltrationRule rule = new DataExfiltrationRule(mapper);
        var def = RuleTestFixtures.rule("DATA_EXFILTRATION", "{\"minBytes\":1000}", Severity.CRITICAL, "T1048");
        FakeRuleContext ctx = new FakeRuleContext();
        ctx.count = 1;

        SecurityEvent small = RuleTestFixtures.event(EventType.DATA_TRANSFER).username("u1")
                .rawPayload("{\"bytesOut\":999}").build();
        SecurityEvent large = RuleTestFixtures.event(EventType.DATA_TRANSFER).username("u1")
                .rawPayload("{\"bytesOut\":5000}").build();
        SecurityEvent garbage = RuleTestFixtures.event(EventType.DATA_TRANSFER).username("u1")
                .rawPayload("not json").build();

        assertThat(rule.evaluate(small, def, ctx)).isEmpty();
        assertThat(rule.evaluate(garbage, def, ctx)).isEmpty();
        assertThat(rule.evaluate(large, def, ctx)).isPresent();
    }
}
