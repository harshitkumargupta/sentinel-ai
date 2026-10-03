package com.sentinelai.detection.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.alert.domain.Alert;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.detection.config.DetectionProperties;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A rule that throws must not prevent the other rules from running. */
class EventProcessorIsolationTest {

    private static final Organization ORG = Organization.builder().id(1L).name("o").build();

    private DetectionRule ruleOfType(String type) {
        return DetectionRule.builder().id(1L).org(ORG).name(type).ruleType(type)
                .severity(Severity.HIGH).version(1).enabled(true).build();
    }

    @Test
    void oneThrowingRuleDoesNotStopOthers() {
        DetectionRuleEvaluator boom = new DetectionRuleEvaluator() {
            public String type() {
                return "BOOM";
            }

            public Optional<AlertDraft> evaluate(SecurityEvent e, DetectionRule r, RuleContext c) {
                throw new RuntimeException("kaboom");
            }
        };
        DetectionRuleEvaluator good = new DetectionRuleEvaluator() {
            public String type() {
                return "GOOD";
            }

            public Optional<AlertDraft> evaluate(SecurityEvent e, DetectionRule r, RuleContext c) {
                return Optional.of(new AlertDraft(Severity.HIGH, "T1", "ok", "user:x", List.of(e.getId())));
            }
        };

        DetectionRuleRepository ruleRepo = mock(DetectionRuleRepository.class);
        when(ruleRepo.findByOrg_IdAndEnabledTrue(1L))
                .thenReturn(List.of(ruleOfType("BOOM"), ruleOfType("GOOD")));
        AlertRepository alertRepo = mock(AlertRepository.class);
        OrganizationRepository orgRepo = mock(OrganizationRepository.class);
        when(orgRepo.getReferenceById(anyLong())).thenReturn(ORG);

        LiveRuleContext ctx = new LiveRuleContext(
                mock(WindowStore.class), mock(DetectionQueries.class), Clock.systemUTC());
        SimpleMeterRegistry meters = new SimpleMeterRegistry();

        SynchronousEventProcessor processor = new SynchronousEventProcessor(
                List.of(boom, good), ruleRepo, mock(com.sentinelai.event.repository.SecurityEventRepository.class),
                alertRepo, orgRepo, ctx, new DetectionProperties(), meters, new ObjectMapper());

        SecurityEvent event = SecurityEvent.builder().id(100L).org(ORG).eventType(EventType.FAILED_LOGIN)
                .severity(Severity.LOW).honeytoken(false).eventTimestamp(Instant.now()).build();

        processor.process(event);

        verify(alertRepo, times(1)).save(any(Alert.class)); // GOOD still fired
        assertThat(meters.counter("sentinel.detection.rule_errors", "rule_type", "BOOM").count())
                .isEqualTo(1.0); // BOOM error counted
    }
}
