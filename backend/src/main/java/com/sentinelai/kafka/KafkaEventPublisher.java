package com.sentinelai.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka-backed {@link EventPublisher}. Serializes payloads to JSON and sends them keyed by
 * entity_key. Sends block briefly for the broker ack so callers (notably the outbox relay) know
 * whether a row was durably published; failures flip a health flag so the ingest fast path can
 * fall back to synchronous processing.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> template;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    public KafkaEventPublisher(KafkaTemplate<String, String> template, ObjectMapper objectMapper,
                               MeterRegistry meters) {
        this.template = template;
        this.objectMapper = objectMapper;
        this.meters = meters;
    }

    @Override
    public boolean publish(String topic, String key, Object payload, String messageId) {
        String json;
        try {
            json = payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            // A payload we cannot even serialize is a programming error, not a transient failure.
            throw new IllegalArgumentException("Cannot serialize pipeline payload for " + topic, e);
        }
        String id = messageId != null ? messageId : UUID.randomUUID().toString();
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
        Headers headers = record.headers();
        headers.add(PipelineHeaders.MESSAGE_ID, id.getBytes(StandardCharsets.UTF_8));
        headers.add(PipelineHeaders.ORIGINAL_TOPIC, topic.getBytes(StandardCharsets.UTF_8));
        return send(record);
    }

    /** Send a pre-built record (used by retry/DLQ routing to preserve pipeline headers). */
    public boolean send(ProducerRecord<String, String> record) {
        try {
            template.send(record).get(5, TimeUnit.SECONDS);
            healthy.set(true);
            meters.counter("sentinel.kafka.publish", "topic", record.topic(), "outcome", "ok").increment();
            return true;
        } catch (Exception e) {
            healthy.set(false);
            meters.counter("sentinel.kafka.publish", "topic", record.topic(), "outcome", "fail").increment();
            log.warn("Kafka publish to {} failed: {}", record.topic(), e.toString());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    @Override
    public boolean isHealthy() {
        return healthy.get();
    }
}
