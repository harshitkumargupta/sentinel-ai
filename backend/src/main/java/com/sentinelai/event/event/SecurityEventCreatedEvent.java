package com.sentinelai.event.event;

/**
 * Published after a {@link com.sentinelai.event.domain.SecurityEvent} is persisted. The detection
 * module listens for this, keeping the event module independent of detection (no compile-time
 * dependency from producers to the engine).
 */
public record SecurityEventCreatedEvent(Long eventId, Long orgId) {
}
