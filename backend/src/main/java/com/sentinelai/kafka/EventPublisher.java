package com.sentinelai.kafka;

/**
 * Seam for publishing pipeline messages. A Kafka-backed implementation exists only when
 * {@code sentinel.kafka.enabled=true}; callers that must degrade gracefully inject this as an
 * {@code ObjectProvider} and fall back to the synchronous path when it is absent or unhealthy.
 */
public interface EventPublisher {

    /**
     * Publish {@code payload} (serialized to JSON) to {@code topic}, keyed by {@code key} so that
     * one entity's messages stay ordered on a single partition. {@code messageId} is a stable,
     * deterministic id (e.g. derived from the aggregate) carried in the {@code x-msg-id} header so
     * that redeliveries and relay retries deduplicate to the same logical message downstream.
     *
     * @return true if the broker acknowledged the write within the send timeout.
     */
    boolean publish(String topic, String key, Object payload, String messageId);

    /** Convenience: publish with a random message id (use when no natural key exists). */
    default boolean publish(String topic, String key, Object payload) {
        return publish(topic, key, payload, java.util.UUID.randomUUID().toString());
    }

    /** Best-effort broker health, used to decide the ingest fast path vs. the sync fallback. */
    boolean isHealthy();
}
