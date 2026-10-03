package com.sentinelai.evaluation.dto;

import java.util.Map;

public record EvaluationResult(
        String runId,
        int labeledEvents,
        int alerts,
        Metrics overall,
        Map<String, Metrics> perRule,
        double meanDetectionLatencySeconds) {
}
