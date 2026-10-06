package com.sentinelai.evaluation.dto;

import java.util.Map;

public record EvaluationResult(
        String runId,
        int labeledEvents,
        int alerts,
        Metrics overall,
        Map<String, Metrics> perRule,
        double meanDetectionLatencySeconds,
        IncidentLevel incidentLevel,
        AlertReduction alertReduction) {

    /** Did each attack scenario produce (ideally exactly) one incident? */
    public record IncidentLevel(
            double precision,
            double recall,
            int attackScenarios,
            int detectedScenarios,
            int exactlyOneScenarios) {
    }

    public record AlertReduction(long events, long alerts, long incidents, double reductionPct) {
    }
}
