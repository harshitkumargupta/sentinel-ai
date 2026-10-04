package com.sentinelai.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.kafka.dlq.DlqMessageRepository;
import com.sentinelai.kafka.idempotency.ProcessedMessageRepository;
import com.sentinelai.kafka.outbox.OutboxRepository;
import com.sentinelai.notification.repository.NotificationRepository;
import com.sentinelai.support.Containers;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for Kafka pipeline integration tests: boots the full app with the pipeline enabled against a
 * Testcontainers Kafka (and the shared Testcontainers MySQL). Retry/relay timings are shortened so
 * retry→DLQ and outbox recovery happen within a test's await window.
 */
@SpringBootTest(properties = {
        "sentinel.kafka.enabled=true",
        "sentinel.kafka.partitions=1",
        "sentinel.kafka.concurrency=1",
        "sentinel.kafka.max-retries=2",
        "sentinel.kafka.retry-backoff-ms=100",
        "sentinel.kafka.max-retry-backoff-ms=400",
        "sentinel.kafka.relay-interval-ms=200",
})
@ActiveProfiles("test")
abstract class KafkaPipelineTestSupport {

    protected static final Long ORG_ID = 1L;

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        KafkaTestTopics.register(registry);
    }

    @Autowired protected OrganizationRepository organizationRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected DetectionRuleRepository ruleRepository;
    @Autowired protected AlertRepository alertRepository;
    @Autowired protected IncidentRepository incidentRepository;
    @Autowired protected NotificationRepository notificationRepository;
    @Autowired protected SecurityEventRepository securityEventRepository;
    @Autowired protected OutboxRepository outboxRepository;
    @Autowired protected ProcessedMessageRepository processedMessageRepository;
    @Autowired protected DlqMessageRepository dlqMessageRepository;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetPipelineState() {
        // Clean mutable tables (consumers may still settle; tests use unique keys to stay isolated).
        notificationRepository.deleteAll();
        incidentRepository.deleteAll();
        alertRepository.deleteAll();
        securityEventRepository.deleteAll();
        dlqMessageRepository.deleteAll();
        outboxRepository.deleteAll();
        processedMessageRepository.deleteAll();
        ruleRepository.deleteAll();
        userRepository.deleteAll();

        Organization org = organizationRepository.findById(ORG_ID).orElseThrow();
        ruleRepository.save(DetectionRule.builder().org(org).name("bf").ruleType("BRUTE_FORCE")
                .config("{\"threshold\":3,\"windowSeconds\":3600,\"groupBy\":\"username\"}")
                .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());
        userRepository.save(User.builder().org(org).username("kadmin")
                .email("kadmin@sentinel.ai").passwordHash(passwordEncoder.encode("x"))
                .role(Role.ADMIN).enabled(true).build());
    }
}
