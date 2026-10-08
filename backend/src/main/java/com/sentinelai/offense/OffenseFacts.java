package com.sentinelai.offense;

import com.sentinelai.incident.domain.IncidentFeedback;

/**
 * The inputs magnitude is computed from, gathered from an incident's alerts and events. Kept as plain
 * data so the calculation is a pure, unit-testable function.
 */
public record OffenseFacts(
        int riskScore,
        boolean privilegedTarget,
        int maxAssetCriticality,
        int distinctRuleTypes,
        int distinctLogSources,
        int distinctEventTypes,
        int threatIntelMatches,
        IncidentFeedback feedback) {
}
