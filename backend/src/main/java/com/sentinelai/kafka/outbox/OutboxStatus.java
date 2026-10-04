package com.sentinelai.kafka.outbox;

/** Lifecycle of an outbox row. Order must match the {@code ENUM(...)} in V23. */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
