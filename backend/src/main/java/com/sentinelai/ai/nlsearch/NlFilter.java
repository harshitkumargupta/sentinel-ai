package com.sentinelai.ai.nlsearch;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;

import java.time.Instant;

/** A validated, allow-listed event filter derived from a natural-language query. */
public record NlFilter(String ip, String user, EventType type, Severity severity, String country,
                       Instant from, Instant to, int limit) {
}
