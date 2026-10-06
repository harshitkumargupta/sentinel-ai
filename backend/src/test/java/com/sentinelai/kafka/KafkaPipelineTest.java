package com.sentinelai.kafka;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.kafka.admin.DlqAdminService;
import com.sentinelai.kafka.dlq.DlqMessage;
import com.sentinelai.kafka.idempotency.ProcessedMessage;
import com.sentinelai.kafka.message.NormalizedEventMessage;
import com.sentinelai.kafka.outbox.OutboxMessage;
import com.sentinelai.kafka.outbox.OutboxService;
import com.sentinelai.kafka.outbox.OutboxStatus;
import com.sentinelai.ingestion.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the Kafka pipeline against Testcontainers Kafka + MySQL. Covers the happy
 * path, idempotent duplicate delivery, retry→DLQ on a poison message, poison not blocking a
 * partition, outbox recovery, DLQ replay and consumer-restart offset resume.
 */
// Each test gets a fresh context (and therefore fresh consumers/producer) so the shared Kafka
// broker's offsets and in-flight messages from one scenario can't bleed into the next.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class KafkaPipelineTest extends KafkaPipelineTestSupport {

    @Autowired private IngestionService ingestionService;
    @Autowired private KafkaEventPublisher publisher;
    @Autowired private OutboxService outboxService;
    @Autowired private KafkaProperties props;
    @Autowired private DlqAdminService dlqAdminService;
    @Autowired private KafkaListenerEndpointRegistry registry;

    private static final Instant BASE = Instant.parse("2026-04-01T00:00:00Z");

    @Test
    void happyPathEndToEnd() {
        for (int i = 0; i < 4; i++) {
            ingestFailedLogin("mallory", BASE.plusSeconds(i * 10L));
        }
        // ingest → outbox → events.normalized → detection → alerts → correlation → incidents →
        // notification. The whole chain is asynchronous, so we await the final side effects.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(incidentRepository.count()).isEqualTo(1);
            assertThat(notificationRepository.count()).isGreaterThanOrEqualTo(1); // HIGH escalation
        });
        assertThat(alertRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void duplicateDeliveryIsANoOp() {
        SecurityEvent event = persistEvent("user:dup");
        NormalizedEventMessage msg = new NormalizedEventMessage(event.getId(), ORG_ID, "user:dup");

        String group = props.getGroups().getDetection();
        publisher.publish(props.getTopics().getNormalized(), "user:dup", msg, "dup-1");
        await().atMost(Duration.ofSeconds(15)).until(() -> processed(group, "dup-1"));

        // Re-deliver the identical message id; the second delivery must be a no-op.
        publisher.publish(props.getTopics().getNormalized(), "user:dup", msg, "dup-1");
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .until(() -> processedCount(group) == 1);
    }

    @Test
    void poisonMessageRetriesThenDeadLetters() {
        // A normalized message for an event that does not exist fails every attempt.
        NormalizedEventMessage poison = new NormalizedEventMessage(999_999L, ORG_ID, "user:ghost");
        publisher.publish(props.getTopics().getNormalized(), "user:ghost", poison, "poison-1");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(dlqMessageRepository.findByStatus(DlqMessage.Status.DEAD))
                        .anyMatch(d -> "detection".equals(d.getHandler())
                                && "poison-1".equals(d.getMessageId())
                                && d.getAttempts() >= props.getMaxRetries()));
    }

    @Test
    void poisonDoesNotBlockThePartition() {
        // Same key → same partition. The poison must not stop the good message behind it.
        publisher.publish(props.getTopics().getNormalized(), "user:mix",
                new NormalizedEventMessage(999_998L, ORG_ID, "user:mix"), "poison-mix");
        SecurityEvent good = persistEvent("user:mix");
        publisher.publish(props.getTopics().getNormalized(), "user:mix",
                new NormalizedEventMessage(good.getId(), ORG_ID, "user:mix"), "good-mix");

        await().atMost(Duration.ofSeconds(20)).until(() -> processed(props.getGroups().getDetection(), "good-mix"));
        await().atMost(Duration.ofSeconds(20)).until(() ->
                dlqMessageRepository.findByStatus(DlqMessage.Status.DEAD).stream()
                        .anyMatch(d -> "poison-mix".equals(d.getMessageId())));
    }

    @Test
    void outboxRelayPublishesPendingRowsAfterCrash() {
        // Simulate a crash between the DB commit and the publish: the event + a PENDING outbox row
        // exist, but the relay has not run yet. The relay must pick it up and drive detection.
        SecurityEvent event = persistEvent("user:crash");
        OutboxMessage row = outboxService.enqueue("event", event.getId(),
                props.getTopics().getNormalized(), "user:crash",
                new NormalizedEventMessage(event.getId(), ORG_ID, "user:crash"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(outboxRepository.findById(row.getId()).orElseThrow().getStatus())
                    .isEqualTo(OutboxStatus.SENT);
            assertThat(processed(props.getGroups().getDetection(), "ob-" + row.getId())).isTrue();
        });
    }

    @Test
    void deadLetterCanBeReplayed() throws Exception {
        SecurityEvent event = persistEvent("user:replay");
        String payload = objectMapper.writeValueAsString(
                new NormalizedEventMessage(event.getId(), ORG_ID, "user:replay"));
        DlqMessage dead = dlqMessageRepository.save(DlqMessage.builder()
                .originalTopic(props.getTopics().getNormalized())
                .handler("detection").messageId("replay-1").kafkaKey("user:replay")
                .payload(payload).attempts(2).error("boom")
                .status(DlqMessage.Status.DEAD).build());

        dlqAdminService.replay(dead.getId(), ORG_ID, adminId(), "127.0.0.1");

        await().atMost(Duration.ofSeconds(20)).until(() -> processed(props.getGroups().getDetection(), "replay-1"));
        assertThat(dlqMessageRepository.findById(dead.getId()).orElseThrow().getStatus())
                .isEqualTo(DlqMessage.Status.REPLAYED);
    }

    @Test
    void consumerRestartResumesFromCommittedOffset() {
        var container = registry.getListenerContainer("detection");
        container.stop();
        try {
            SecurityEvent event = persistEvent("user:restart");
            publisher.publish(props.getTopics().getNormalized(), "user:restart",
                    new NormalizedEventMessage(event.getId(), ORG_ID, "user:restart"), "restart-1");
            // While stopped, detection must not have processed it.
            await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4))
                    .until(() -> !processed(props.getGroups().getDetection(), "restart-1"));
        } finally {
            container.start();
        }
        // On restart it resumes from the committed offset and consumes the backlog.
        await().atMost(Duration.ofSeconds(20)).until(() -> processed(props.getGroups().getDetection(), "restart-1"));
    }

    // --- helpers -----------------------------------------------------------------------------

    private void ingestFailedLogin(String user, Instant ts) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("eventType", "FAILED_LOGIN");
        p.put("severity", "LOW");
        p.put("username", user);
        p.put("sourceIp", "203.0.113.5");
        p.put("eventTimestamp", ts.toString());
        ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
    }

    private SecurityEvent persistEvent(String entityKey) {
        return securityEventRepository.save(SecurityEvent.builder()
                .org(organizationRepository.getReferenceById(ORG_ID))
                .eventType(EventType.FAILED_LOGIN)
                .severity(Severity.LOW)
                .entityKey(entityKey)
                .eventTimestamp(Instant.now())
                .build());
    }

    private boolean processed(String group, String messageId) {
        return processedMessageRepository.existsById(new ProcessedMessage.Key(group, messageId));
    }

    private long processedCount(String group) {
        return processedMessageRepository.countByIdConsumerGroup(group);
    }

    private Long adminId() {
        return userRepository.findByRole(Role.ADMIN).get(0).getId();
    }
}
