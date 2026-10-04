package com.sentinelai.kafka;

/** Kafka record header names used across the pipeline (idempotency, retry/DLQ routing). */
public final class PipelineHeaders {

    private PipelineHeaders() {
    }

    /** Stable message id set at first publish; preserved through retry/DLQ for idempotency. */
    public static final String MESSAGE_ID = "x-msg-id";
    /** Logical handler the message is destined for (so the shared retry consumer can dispatch). */
    public static final String HANDLER = "x-handler";
    /** The topic the message was originally published to. */
    public static final String ORIGINAL_TOPIC = "x-original-topic";
    /** Retry attempt number (1-based). */
    public static final String ATTEMPT = "x-attempt";
    /** Error summary captured when a message fails. */
    public static final String ERROR = "x-error";
}
