package com.sentinelai.kafka.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinelai.audit.service.AuditService;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.kafka.EventPublisher;
import com.sentinelai.kafka.KafkaProperties;
import com.sentinelai.kafka.message.RawIngestMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;

/**
 * Chaos + observability hooks for the demo: pause/resume a consumer group's container (so lag can be
 * watched growing and draining) and publish a burst of raw events. Pausing and resuming are
 * audit-logged.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class ChaosService {

    private final KafkaListenerEndpointRegistry registry;
    private final EventPublisher publisher;
    private final KafkaProperties props;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public ChaosService(KafkaListenerEndpointRegistry registry, EventPublisher publisher,
                        KafkaProperties props, ObjectMapper objectMapper, AuditService auditService) {
        this.registry = registry;
        this.publisher = publisher;
        this.props = props;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    public void pause(String listenerId, Long orgId, Long actorId, String ip) {
        container(listenerId).pause();
        auditService.record(orgId, actorId, "KAFKA_PAUSE", "consumer", null,
                "{\"listener\":\"" + listenerId + "\"}", ip);
        log.warn("Chaos: paused consumer '{}'", listenerId);
    }

    public void resume(String listenerId, Long orgId, Long actorId, String ip) {
        container(listenerId).resume();
        auditService.record(orgId, actorId, "KAFKA_RESUME", "consumer", null,
                "{\"listener\":\"" + listenerId + "\"}", ip);
        log.warn("Chaos: resumed consumer '{}'", listenerId);
    }

    /** Publish {@code count} failed-login events for one entity onto {@code events.raw}. */
    public int burst(int count, Long orgId) {
        String key = "user:chaos-user";
        int published = 0;
        for (int i = 0; i < count; i++) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("username", "chaos-user");
            payload.put("sourceIp", "10.0.0." + (i % 255));
            payload.put("success", false);
            RawIngestMessage msg = new RawIngestMessage(orgId, null, "auth", payload, null);
            if (publisher.publish(props.getTopics().getRaw(), key, msg)) {
                published++;
            }
        }
        log.info("Chaos: published burst of {}/{} events to {}", published, count, props.getTopics().getRaw());
        return published;
    }

    private MessageListenerContainer container(String listenerId) {
        MessageListenerContainer c = registry.getListenerContainer(listenerId);
        if (c == null) {
            throw new NotFoundException("No consumer with id: " + listenerId);
        }
        return c;
    }
}
