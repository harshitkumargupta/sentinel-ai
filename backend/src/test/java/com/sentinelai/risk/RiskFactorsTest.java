package com.sentinelai.risk;

import com.sentinelai.alert.domain.Alert;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.risk.factor.AssetCriticalityFactor;
import com.sentinelai.risk.factor.FrequencyFactor;
import com.sentinelai.risk.factor.HoneytokenFactor;
import com.sentinelai.risk.factor.MitreStageFactor;
import com.sentinelai.risk.factor.RepetitionFactor;
import com.sentinelai.risk.factor.SeverityFactor;
import com.sentinelai.risk.factor.UserBehaviorFactor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskFactorsTest {

    private static final Organization ORG = Organization.builder().id(1L).name("o").build();
    private final RiskProperties props = new RiskProperties();

    private Alert alert(Severity sev, String ruleType, String mitre) {
        return Alert.builder().id(1L).org(ORG).ruleType(ruleType).severity(sev).mitreTechnique(mitre).message("m").build();
    }

    private SecurityEvent event(EventType type, Byte crit, Instant ts) {
        return SecurityEvent.builder().id(1L).org(ORG).eventType(type).severity(Severity.LOW)
                .assetCriticality(crit).honeytoken(false).eventTimestamp(ts).build();
    }

    private RiskContext ctx(List<Alert> alerts, List<SecurityEvent> events) {
        return new RiskContext(alerts, events, props);
    }

    @Test
    void severityFactorUsesHighestAlertSeverity() {
        var r = new SeverityFactor().score(ctx(
                List.of(alert(Severity.MEDIUM, "X", null), alert(Severity.HIGH, "Y", null)), List.of()));
        assertThat(r.points()).isEqualTo(props.getSeverityPoints().get(Severity.HIGH));
    }

    @Test
    void frequencyFactorScalesWithEventsAndCaps() {
        var three = new FrequencyFactor().score(ctx(List.of(),
                List.of(event(EventType.OTHER, null, Instant.now()), event(EventType.OTHER, null, Instant.now()),
                        event(EventType.OTHER, null, Instant.now()))));
        assertThat(three.points()).isEqualTo(6);
    }

    @Test
    void repetitionFactorCountsExtraAlerts() {
        var r = new RepetitionFactor().score(ctx(
                List.of(alert(Severity.LOW, "A", null), alert(Severity.LOW, "A", null),
                        alert(Severity.LOW, "A", null)), List.of()));
        assertThat(r.points()).isEqualTo(10); // 2 extra * 5
    }

    @Test
    void assetCriticalityFactorScalesWithLevel() {
        var r = new AssetCriticalityFactor().score(ctx(List.of(),
                List.of(event(EventType.OTHER, (byte) 3, Instant.now()))));
        assertThat(r.points()).isEqualTo(24); // 3 * 8
    }

    @Test
    void honeytokenFactorFiresOnHoneytokenAlert() {
        var r = new HoneytokenFactor().score(ctx(List.of(alert(Severity.HIGH, "HONEYTOKEN", null)), List.of()));
        assertThat(r.points()).isEqualTo(props.getHoneytokenPoints());
    }

    @Test
    void userBehaviorFactorAddsSignalsAndCaps() {
        var oddTs = Instant.parse("2026-02-01T03:00:00Z");
        var r = new UserBehaviorFactor().score(ctx(
                List.of(alert(Severity.HIGH, "SUSPICIOUS_LOGIN", null)),
                List.of(event(EventType.ABNORMAL_ACCESS, null, oddTs))));
        assertThat(r.points()).isEqualTo(props.getUserBehaviorCap()); // 15+10+10 capped at 30
    }

    @Test
    void mitreStageFactorTakesHighestWeight() {
        var r = new MitreStageFactor().score(ctx(
                List.of(alert(Severity.HIGH, "A", "T1110"), alert(Severity.HIGH, "B", "T1548")), List.of()));
        assertThat(r.points()).isEqualTo(props.getMitreStageWeights().get("T1548"));
    }

    private RiskService service() {
        return new RiskService(List.of(new SeverityFactor(), new FrequencyFactor(), new RepetitionFactor(),
                new AssetCriticalityFactor(), new HoneytokenFactor(), new UserBehaviorFactor(),
                new MitreStageFactor()), props);
    }

    @Test
    void scoreIsDeterministic() {
        var alerts = List.of(alert(Severity.HIGH, "BRUTE_FORCE", "T1110"));
        var events = List.of(event(EventType.FAILED_LOGIN, (byte) 2, Instant.now()));
        RiskResult a = service().score(alerts, events);
        RiskResult b = service().score(alerts, events);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void totalScoreIsCappedAt100() {
        var alerts = List.of(
                alert(Severity.CRITICAL, "HONEYTOKEN", "T1548"),
                alert(Severity.CRITICAL, "SUSPICIOUS_LOGIN", "T1078.001"),
                alert(Severity.CRITICAL, "X", "T1548"));
        var events = List.of(
                event(EventType.ABNORMAL_ACCESS, (byte) 4, Instant.parse("2026-02-01T03:00:00Z")),
                event(EventType.FAILED_LOGIN, (byte) 4, Instant.now()));
        assertThat(service().score(alerts, events).score()).isEqualTo(100);
    }
}
