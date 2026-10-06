package com.sentinelai.detection.tuning;

/**
 * Per-rule tuning summary and (optionally) a suggested threshold change, computed from analyst
 * feedback by replaying the rule over the labeled events. Never auto-applied.
 */
public record RuleTuning(
        Long ruleId,
        String ruleName,
        String ruleType,
        int firedFalsePositives,
        int firedTruePositives,
        double fpRate,
        int sampleSize,
        boolean enoughSamples,
        ThresholdSuggestion suggestion) {

    /** "raise threshold 5 -> 8: removes X% of false positives, loses Y true positives". */
    public record ThresholdSuggestion(String param, int from, int to, int fpRemoved,
                                      double fpRemovedPct, int tpLost, String suggestedConfig, String summary) {
    }
}
