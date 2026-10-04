package com.sentinelai.kafka;

import com.sentinelai.support.Containers;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.UUID;

/**
 * Registers the Kafka bootstrap plus a per-context-unique set of topic and consumer-group names.
 * The Testcontainers Kafka broker is shared across the whole test JVM, so giving every test context
 * its own topics/groups keeps committed offsets and in-flight messages from one context or test
 * class from bleeding into another.
 */
final class KafkaTestTopics {

    private KafkaTestTopics() {
    }

    static void register(DynamicPropertyRegistry registry) {
        String s = UUID.randomUUID().toString().substring(0, 8);
        registry.add("sentinel.kafka.bootstrap-servers", () -> Containers.kafka().getBootstrapServers());

        registry.add("sentinel.kafka.topics.raw", () -> "raw-" + s);
        registry.add("sentinel.kafka.topics.normalized", () -> "normalized-" + s);
        registry.add("sentinel.kafka.topics.alerts", () -> "alerts-" + s);
        registry.add("sentinel.kafka.topics.incidents", () -> "incidents-" + s);
        registry.add("sentinel.kafka.topics.retry", () -> "retry-" + s);
        registry.add("sentinel.kafka.topics.dlq", () -> "dlq-" + s);

        registry.add("sentinel.kafka.groups.raw-ingest", () -> "g-raw-" + s);
        registry.add("sentinel.kafka.groups.detection", () -> "g-detection-" + s);
        registry.add("sentinel.kafka.groups.correlation", () -> "g-correlation-" + s);
        registry.add("sentinel.kafka.groups.analytics", () -> "g-analytics-" + s);
        registry.add("sentinel.kafka.groups.notification", () -> "g-notification-" + s);
        registry.add("sentinel.kafka.groups.retry", () -> "g-retry-" + s);
        registry.add("sentinel.kafka.groups.dlq", () -> "g-dlq-" + s);
    }
}
