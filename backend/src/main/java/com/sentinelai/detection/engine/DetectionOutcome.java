package com.sentinelai.detection.engine;

import com.sentinelai.event.domain.SecurityEvent;

import java.util.List;

/**
 * Produced by a {@link DetectionStrategy} when a rule fires. The engine turns it into a new or
 * correlated incident and links the matched events.
 *
 * @param correlationKey stable key identifying this rule+entity, so repeated firings correlate
 *                       into one incident rather than creating duplicates
 * @param title          human-readable incident title
 * @param matchedEvents  events that contributed to the firing (to link to the incident)
 * @param reason         short machine-readable reason, stored in the incident risk breakdown
 */
public record DetectionOutcome(
        String correlationKey,
        String title,
        List<SecurityEvent> matchedEvents,
        String reason) {
}
