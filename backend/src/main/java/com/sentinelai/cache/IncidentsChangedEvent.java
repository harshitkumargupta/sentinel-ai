package com.sentinelai.cache;

/** Published when incidents/alerts change, so tenant dashboard caches are evicted event-driven. */
public record IncidentsChangedEvent(Long orgId) {
}
