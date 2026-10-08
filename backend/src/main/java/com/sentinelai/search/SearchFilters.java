package com.sentinelai.search;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventOutcome;
import com.sentinelai.event.domain.EventType;

import java.time.Instant;

/** Structured search filters (all optional), AND-ed with the query. */
public record SearchFilters(Instant from, Instant to, Long sourceId, String ip, String user,
                            EventType eventType, EventOutcome outcome, Severity severity) {
}
