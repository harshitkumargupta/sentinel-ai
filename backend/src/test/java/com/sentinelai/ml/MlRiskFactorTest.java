package com.sentinelai.ml;

import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.risk.FactorResult;
import com.sentinelai.risk.RiskContext;
import com.sentinelai.risk.RiskProperties;
import com.sentinelai.risk.RiskResult;
import com.sentinelai.risk.RiskService;
import com.sentinelai.risk.factor.HoneytokenFactor;
import com.sentinelai.risk.factor.MlRiskFactor;
import com.sentinelai.alert.domain.Alert;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** ML risk factor: fallback, cap, and that it can never override hard rules (only add, capped). */
class MlRiskFactorTest {

    private static final Organization ORG = Organization.builder().id(1L).name("o").build();
    private final MlFeatureExtractor extractor = new MlFeatureExtractor();
    private final MlProperties props = new MlProperties();

    private MlScoringClient fixed(Integer score) {
        return new MlScoringClient() {
            public Optional<MlScore> score(List<Double> f, String kind) {
                return score == null ? Optional.empty() : Optional.of(new MlScore(score, "v1", List.of()));
            }
            public Optional<MlModelInfo> modelInfo() {
                return Optional.empty();
            }
        };
    }

    private RiskContext ctx() {
        return new RiskContext(List.of(), List.of(), new RiskProperties());
    }

    @Test
    void fallsBackToZeroWhenServiceUnavailable() {
        var f = new MlRiskFactor(fixed(null), extractor, props);
        assertThat(f.score(ctx()).points()).isZero();
    }

    @Test
    void respectsWeightCap() {
        var f = new MlRiskFactor(fixed(100), extractor, props); // weightCap default 30
        assertThat(f.score(ctx()).points()).isEqualTo(props.getWeightCap());
    }

    @Test
    void scalesProportionally() {
        var f = new MlRiskFactor(fixed(50), extractor, props);
        assertThat(f.score(ctx()).points()).isEqualTo(15); // 50% of cap 30
    }

    @Test
    void modelCannotOverrideHardRuleSeverity() {
        // Honeytoken is a hard rule (60 pts -> HIGH floor). Even with ML down (0), severity holds.
        var honeyAlert = Alert.builder().id(1L).org(ORG).ruleType("HONEYTOKEN")
                .severity(Severity.CRITICAL).message("m").build();
        var service = new RiskService(List.of(new HoneytokenFactor(),
                new MlRiskFactor(fixed(null), extractor, props)), new RiskProperties());
        RiskResult down = service.score(List.of(honeyAlert), List.of());
        assertThat(down.severity()).isEqualTo(Severity.HIGH);

        // With ML up it can only ADD — never lower the honeytoken floor.
        var up = new RiskService(List.of(new HoneytokenFactor(),
                new MlRiskFactor(fixed(100), extractor, props)), new RiskProperties());
        assertThat(up.score(List.of(honeyAlert), List.of()).score())
                .isGreaterThanOrEqualTo(down.score());
        assertThat(up.score(List.of(honeyAlert), List.of()).breakdown())
                .anyMatch(fr -> fr.name().equals("ml_model"));
    }
}
