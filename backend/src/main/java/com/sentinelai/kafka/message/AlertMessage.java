package com.sentinelai.kafka.message;

/** Carried on the {@code alerts} topic: a detection rule firing, keyed by entity. */
public record AlertMessage(Long alertId, Long orgId, String entityKey, String severity) {
}
