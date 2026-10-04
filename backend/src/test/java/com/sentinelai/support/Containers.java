package com.sentinelai.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Lazily-started, JVM-wide singleton containers for the optional dependencies (Redis, Kafka). MySQL
 * is handled separately by the Testcontainers JDBC URL in {@code application-test.yml}. Containers
 * are started once and reused across tests; Testcontainers' Ryuk reaper stops them at JVM exit, so
 * there is no explicit stop (which keeps them shared and fast).
 */
public final class Containers {

    private Containers() {
    }

    private static volatile GenericContainer<?> redis;
    private static volatile KafkaContainer kafka;

    @SuppressWarnings("resource")
    public static synchronized GenericContainer<?> redis() {
        if (redis == null) {
            GenericContainer<?> c = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);
            c.start();
            redis = c;
        }
        return redis;
    }

    @SuppressWarnings("resource")
    public static synchronized KafkaContainer kafka() {
        if (kafka == null) {
            KafkaContainer c = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
            c.start();
            kafka = c;
        }
        return kafka;
    }
}
