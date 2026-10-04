package com.sentinelai.kafka.message;

/**
 * Carried on {@code events.normalized}: a reference to the persisted {@code SecurityEvent}. The
 * event body itself lives in the DB (written in the same transaction as the outbox row), so the
 * message stays small and consumers always read a consistent, committed row.
 */
public record NormalizedEventMessage(Long eventId, Long orgId, String entityKey) {
}
