package com.sentinelai.detection;

import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.config.DetectionProperties;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.event.SecurityEventCreatedEvent;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end detection engine tests (rules + event publication + incident creation/correlation).
 * Covers the happy paths and failure/guard paths (below threshold, feature flag off).
 */
class DetectionEngineTest extends IntegrationTestSupport {

    @Autowired
    private DetectionRuleRepository ruleRepository;
    @Autowired
    private IncidentEventRepository incidentEventRepository;
    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private DetectionProperties detectionProperties;

    private Organization org;

    @BeforeEach
    void setUpDetection() {
        ruleRepository.deleteAll();
        org = organizationRepository.findById(ORG_ID).orElseThrow();
        detectionProperties.setEnabled(true);
    }

    private void thresholdRule(int threshold, int windowSeconds) {
        ruleRepository.save(DetectionRule.builder()
                .org(org)
                .name("Brute force test")
                .ruleType("THRESHOLD")
                .config("{\"eventType\":\"FAILED_LOGIN\",\"threshold\":" + threshold
                        + ",\"windowSeconds\":" + windowSeconds + ",\"groupBy\":\"username\"}")
                .enabled(true)
                .severity(Severity.HIGH)
                .version(1)
                .build());
    }

    private void ingest(EventType type, String username, String geoCountry) {
        SecurityEvent e = securityEventRepository.save(SecurityEvent.builder()
                .org(org)
                .eventType(type)
                .severity(Severity.MEDIUM)
                .username(username)
                .sourceIp("203.0.113.9")
                .geoCountry(geoCountry)
                .honeytoken(false)
                .eventTimestamp(Instant.now())
                .build());
        publisher.publishEvent(new SecurityEventCreatedEvent(e.getId(), org.getId()));
    }

    @Test
    void bruteForceCreatesIncidentAtThreshold() {
        thresholdRule(3, 3600);

        ingest(EventType.FAILED_LOGIN, "mallory", null);
        ingest(EventType.FAILED_LOGIN, "mallory", null);
        assertThat(incidentRepository.count()).isZero(); // below threshold

        ingest(EventType.FAILED_LOGIN, "mallory", null); // 3rd -> fires

        assertThat(incidentRepository.count()).isEqualTo(1);
        var incident = incidentRepository.findAll().get(0);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(incident.getCorrelationKey()).contains("mallory");
        assertThat(incidentEventRepository.countById_IncidentId(incident.getId())).isEqualTo(3);
    }

    @Test
    void repeatedFiringsCorrelateIntoOneIncident() {
        thresholdRule(3, 3600);
        for (int i = 0; i < 3; i++) {
            ingest(EventType.FAILED_LOGIN, "trudy", null);
        }
        assertThat(incidentRepository.count()).isEqualTo(1);

        ingest(EventType.FAILED_LOGIN, "trudy", null); // 4th -> correlates, no new incident

        assertThat(incidentRepository.count()).isEqualTo(1);
        var incident = incidentRepository.findAll().get(0);
        assertThat(incidentEventRepository.countById_IncidentId(incident.getId())).isEqualTo(4);
    }

    @Test
    void belowThresholdCreatesNoIncident() {
        thresholdRule(5, 3600);
        for (int i = 0; i < 4; i++) {
            ingest(EventType.FAILED_LOGIN, "oscar", null);
        }
        assertThat(incidentRepository.count()).isZero();
    }

    @Test
    void disabledEngineCreatesNoIncident() {
        thresholdRule(2, 3600);
        detectionProperties.setEnabled(false);
        try {
            ingest(EventType.FAILED_LOGIN, "peggy", null);
            ingest(EventType.FAILED_LOGIN, "peggy", null);
            assertThat(incidentRepository.count()).isZero();
        } finally {
            detectionProperties.setEnabled(true);
        }
    }

    @Test
    void geoVelocityFiresOnNewCountry() {
        ruleRepository.save(DetectionRule.builder()
                .org(org)
                .name("Impossible travel test")
                .ruleType("GEO_VELOCITY")
                .config("{\"eventType\":\"SUSPICIOUS_LOGIN\"}")
                .enabled(true)
                .severity(Severity.HIGH)
                .version(1)
                .build());

        ingest(EventType.SUSPICIOUS_LOGIN, "victor", "US"); // first login, no prior -> no fire
        assertThat(incidentRepository.count()).isZero();

        ingest(EventType.SUSPICIOUS_LOGIN, "victor", "RU"); // new country -> fires

        assertThat(incidentRepository.count()).isEqualTo(1);
        assertThat(incidentRepository.findAll().get(0).getCorrelationKey()).contains("geo");
    }
}
