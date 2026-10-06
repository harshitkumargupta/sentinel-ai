package com.sentinelai.detection.tuning;

import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentEvent;
import com.sentinelai.incident.domain.IncidentFeedback;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Exact tuning math on a known labeled set: one false-positive incident, one true-positive. */
@SpringBootTest(properties = {"sentinel.ai.enabled=false", "sentinel.kafka.enabled=false",
        "sentinel.tuning.min-samples=1"})
@ActiveProfiles("test")
class TuningServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Instant BASE = Instant.parse("2026-05-01T00:00:00Z");

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private DetectionRuleRepository ruleRepository;
    @Autowired private SecurityEventRepository eventRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private IncidentEventRepository incidentEventRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private com.sentinelai.playbook.repository.PlaybookActionRepository playbookActionRepository;
    @Autowired private com.sentinelai.ai.repository.AiAnalysisRepository aiAnalysisRepository;
    @Autowired private TuningService tuningService;

    private DetectionRule rule;

    @BeforeEach
    void seed() {
        playbookActionRepository.deleteAll();
        aiAnalysisRepository.deleteAll();
        notificationRepository.deleteAll();
        incidentRepository.deleteAll();
        eventRepository.deleteAll();
        ruleRepository.deleteAll();

        Organization org = organizationRepository.findById(ORG_ID).orElseThrow();
        rule = ruleRepository.save(DetectionRule.builder().org(org).name("bf").ruleType("BRUTE_FORCE")
                .config("{\"threshold\":3,\"windowSeconds\":3600,\"groupBy\":\"username\"}")
                .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());

        // fp-user: 4 failed logins (fires at threshold 3, NOT at 5) -> FALSE_POSITIVE incident.
        incidentWith("fp", 4, IncidentFeedback.FALSE_POSITIVE, 0);
        // tp-user: 6 failed logins (fires at 3 AND 5) -> TRUE_POSITIVE incident.
        incidentWith("tp", 6, IncidentFeedback.TRUE_POSITIVE, 100);
    }

    private void incidentWith(String user, int count, IncidentFeedback feedback, int offset) {
        Organization org = organizationRepository.getReferenceById(ORG_ID);
        Incident incident = incidentRepository.save(Incident.builder()
                .org(org).title("inc-" + user).status(IncidentStatus.OPEN).severity(Severity.HIGH)
                .feedback(feedback).correlationKey("user:" + user).riskScore(50).build());
        for (int i = 0; i < count; i++) {
            SecurityEvent e = eventRepository.save(SecurityEvent.builder()
                    .org(org).eventType(EventType.FAILED_LOGIN).severity(Severity.LOW)
                    .username(user).sourceIp("203.0.113.1").entityKey("user:" + user)
                    .honeytoken(false).eventTimestamp(BASE.plusSeconds(offset + i * 10L)).build());
            incidentEventRepository.save(new IncidentEvent(incident, e));
        }
    }

    @Test
    void computesExactFpRateAndThresholdSuggestion() {
        RuleTuning t = tuningService.forRule(rule.getId(), ORG_ID);

        assertThat(t.firedFalsePositives()).isEqualTo(1);
        assertThat(t.firedTruePositives()).isEqualTo(1);
        assertThat(t.sampleSize()).isEqualTo(2);
        assertThat(t.fpRate()).isEqualTo(0.5);

        assertThat(t.suggestion()).isNotNull();
        assertThat(t.suggestion().from()).isEqualTo(3);
        assertThat(t.suggestion().to()).isEqualTo(5);
        assertThat(t.suggestion().fpRemoved()).isEqualTo(1);
        assertThat(t.suggestion().tpLost()).isEqualTo(0);
        assertThat(t.suggestion().fpRemovedPct()).isEqualTo(1.0);
    }

}
