package com.sentinelai.incident.correlation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.alert.domain.Alert;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.incident.repository.IncidentAlertRepository;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationServiceTest extends IntegrationTestSupport {

    @Autowired private IngestionService ingestionService;
    @Autowired private DetectionRuleRepository ruleRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private IncidentAlertRepository incidentAlertRepository;
    @Autowired private CorrelationService correlationService;
    @Autowired private ObjectMapper objectMapper;

    private static final Instant BASE = Instant.parse("2026-04-01T00:00:00Z");

    @BeforeEach
    void setUpCorrelation() {
        alertRepository.deleteAll();
        ruleRepository.deleteAll();
        Organization org = organizationRepository.findById(ORG_ID).orElseThrow();
        ruleRepository.save(DetectionRule.builder().org(org).name("bf").ruleType("BRUTE_FORCE")
                .config("{\"threshold\":3,\"windowSeconds\":3600,\"groupBy\":\"username\"}")
                .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());
    }

    private void ingestFailedLogin(String user, Instant ts) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("eventType", "FAILED_LOGIN");
        p.put("severity", "LOW");
        p.put("username", user);
        p.put("sourceIp", "203.0.113.5");
        p.put("eventTimestamp", ts.toString());
        ingestionService.ingest(ORG_ID, "generic", objectMapper.valueToTree(p), null);
    }

    @Test
    void bruteForceAlertsCorrelateIntoOneIncident() {
        for (int i = 0; i < 4; i++) {
            ingestFailedLogin("mallory", BASE.plusSeconds(i * 10L));
        }
        assertThat(incidentRepository.count()).isEqualTo(1);
        var incident = incidentRepository.findAll().get(0);
        assertThat(incident.getRiskScore()).isGreaterThan(0);
        assertThat(incident.getSeverity()).isNotNull();
        // Alerts fired on the 3rd and 4th events, both joined the one incident.
        assertThat(incidentAlertRepository.countById_IncidentId(incident.getId())).isGreaterThanOrEqualTo(2);
    }

    @Test
    void burstsOutsideWindowCreateSeparateIncidents() {
        for (int i = 0; i < 4; i++) {
            ingestFailedLogin("trudy", BASE.plusSeconds(i * 10L));
        }
        for (int i = 0; i < 4; i++) {
            ingestFailedLogin("trudy", BASE.plusSeconds(7200 + i * 10L)); // +2h > 3600s window
        }
        assertThat(incidentRepository.count()).isEqualTo(2);
    }

    @Test
    void reprocessingAnAlertIsIdempotent() {
        for (int i = 0; i < 4; i++) {
            ingestFailedLogin("oscar", BASE.plusSeconds(i * 10L));
        }
        var incident = incidentRepository.findAll().get(0);
        long before = incidentAlertRepository.countById_IncidentId(incident.getId());

        Alert existing = alertRepository.findAll().get(0);
        assertThat(correlationService.correlate(existing)).isNull(); // already correlated
        assertThat(incidentAlertRepository.countById_IncidentId(incident.getId())).isEqualTo(before);
    }
}
