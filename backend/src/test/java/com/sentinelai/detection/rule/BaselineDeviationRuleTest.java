package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.baseline.BehavioralBaselineService;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.support.RuleTestFixtures;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BaselineDeviationRuleTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private BehavioralBaselineService baselines(Optional<Double> z) {
        return new BehavioralBaselineService() {
            public void observe(String e, String m, double v) {
            }
            public Optional<Double> zScore(String e, String m, double v) {
                return z;
            }
            public Optional<Stat> stat(String e, String m) {
                return Optional.of(new Stat(100, 13.0, 2.0));
            }
        };
    }

    private SecurityEvent login() {
        return RuleTestFixtures.event(EventType.SUSPICIOUS_LOGIN).username("bob")
                .eventTimestamp(Instant.parse("2026-02-01T03:00:00Z")).build();
    }

    @Test
    void firesOnLargeZScore() {
        var rule = new BaselineDeviationRule(baselines(Optional.of(4.2)), mapper);
        var def = RuleTestFixtures.rule("BASELINE_DEVIATION", "{\"zThreshold\":3}", Severity.MEDIUM, "T1078");
        assertThat(rule.evaluate(login(), def, null)).isPresent();
    }

    @Test
    void doesNotFireNearMiss() {
        var rule = new BaselineDeviationRule(baselines(Optional.of(2.0)), mapper);
        var def = RuleTestFixtures.rule("BASELINE_DEVIATION", "{\"zThreshold\":3}", Severity.MEDIUM, "T1078");
        assertThat(rule.evaluate(login(), def, null)).isEmpty();
    }

    @Test
    void skipsColdStart() {
        var rule = new BaselineDeviationRule(baselines(Optional.empty()), mapper);
        var def = RuleTestFixtures.rule("BASELINE_DEVIATION", "{\"zThreshold\":3}", Severity.MEDIUM, "T1078");
        assertThat(rule.evaluate(login(), def, null)).isEmpty();
    }
}
