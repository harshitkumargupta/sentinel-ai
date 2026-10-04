package com.sentinelai.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * A single stage of business logic in the pipeline. The same handler is invoked both by its live
 * {@code @KafkaListener} and by the shared retry consumer, so a retried message runs through exactly
 * the same code. Implementations do their own payload parsing and are expected to be idempotent via
 * the surrounding {@link IdempotentExecutor} transaction.
 */
public interface PipelineHandler {

    /** Stable logical name, carried in the {@code x-handler} header so retries can be dispatched. */
    String name();

    /** The consumer group this handler belongs to (used as the idempotency scope). */
    String group();

    /** Process one record. Throwing routes the message to retry and, once exhausted, to the DLQ. */
    void handle(ConsumerRecord<String, String> record) throws Exception;
}
