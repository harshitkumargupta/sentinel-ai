package com.sentinelai.kafka;

import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * When the Kafka pipeline is enabled but the broker is unreachable, ingestion must degrade
 * gracefully to the synchronous in-process detection path (so events are still detected) rather
 * than fail. We simulate an unreachable broker by forcing the publisher unhealthy.
 */
@SpringBootTest(properties = "sentinel.kafka.enabled=true")
@ActiveProfiles("test")
class KafkaDownFallbackTest {

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        KafkaTestTopics.register(registry);
    }

    @MockBean private KafkaEventPublisher publisher; // stands in for an unreachable broker

    @Autowired private IngestionService ingestionService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private DetectionRuleRepository ruleRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private SecurityEventRepository securityEventRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private com.sentinelai.playbook.repository.PlaybookActionRepository playbookActionRepository;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        when(publisher.isHealthy()).thenReturn(false); // broker unreachable
        notificationRepository.deleteAll();
        playbookActionRepository.deleteAll(); // FK RESTRICT -> clear before incidents
        incidentRepository.deleteAll();
        alertRepository.deleteAll();
        securityEventRepository.deleteAll();
        ruleRepository.deleteAll();
        Organization org = organizationRepository.findById(1L).orElseThrow();
        ruleRepository.save(DetectionRule.builder().org(org).name("bf").ruleType("BRUTE_FORCE")
                .config("{\"threshold\":3,\"windowSeconds\":3600,\"groupBy\":\"username\"}")
                .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());
    }

    @Test
    void fallsBackToSynchronousDetectionWhenBrokerUnreachable() {
        IngestionService.IngestOutcome last = null;
        for (int i = 0; i < 4; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("eventType", "FAILED_LOGIN");
            p.put("severity", "LOW");
            p.put("username", "fallback-user");
            p.put("sourceIp", "198.51.100.9");
            p.put("eventTimestamp", Instant.parse("2026-04-01T00:00:00Z").plusSeconds(i * 10L).toString());
            last = ingestionService.ingest(1L, null, "generic", objectMapper.valueToTree(p), null);
        }
        // Dispatched synchronously, and detection ran inline — no outbox, no Kafka needed.
        assertThat(last.dispatch()).isEqualTo(IngestionService.Dispatch.SYNC);
        assertThat(incidentRepository.count()).isEqualTo(1);
    }
}
