package com.sentinelai.detection.engine;

import com.sentinelai.common.domain.Severity;

import java.util.List;

/**
 * A rule's decision to fire. The engine turns this into a persisted {@code Alert}, adding the rule
 * snapshot, triggering event, and run id.
 */
public record AlertDraft(
        Severity severity,
        String mitreTechnique,
        String message,
        String entityKey,
        List<Long> matchedEventIds) {
}
