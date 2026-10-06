package com.sentinelai.detection.backtest;

import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestServiceTest extends IntegrationTestSupport {

    @Autowired
    private BacktestService backtestService;
    @Autowired
    private DetectionRuleRepository ruleRepository;
    @Autowired
    private AlertRepository alertRepository;

    private static final Instant BASE = Instant.parse("2026-03-01T00:00:00Z");

    @BeforeEach
    void setUpBacktest() {
        ruleRepository.deleteAll();
        alertRepository.deleteAll();
    }

    @Test
    void backtestIsDryRunAndSavesNoAlerts() {
        Organization org = organizationRepository.findById(ORG_ID).orElseThrow();
        DetectionRule rule = ruleRepository.save(DetectionRule.builder()
                .org(org).name("bt").ruleType("BRUTE_FORCE")
                .config("{\"threshold\":3,\"windowSeconds\":3600,\"groupBy\":\"username\"}")
                .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());

        for (int i = 0; i < 4; i++) {
            securityEventRepository.save(SecurityEvent.builder()
                    .org(org).eventType(EventType.FAILED_LOGIN).severity(Severity.LOW)
                    .username("bt_user").sourceIp("203.0.113.5").honeytoken(false)
                    .eventTimestamp(BASE.plusSeconds(i * 10L)).build());
        }

        BacktestResult result = backtestService.backtest(
                rule.getId(), ORG_ID, null, BASE.minusSeconds(60), BASE.plusSeconds(7200));

        assertThat(result.eventsScanned()).isEqualTo(4);
        assertThat(result.alertsFired()).isGreaterThanOrEqualTo(1);
        assertThat(alertRepository.count()).isZero(); // dry run persisted nothing
    }
}
