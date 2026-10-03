package com.sentinelai.incident.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.risk.FactorResult;
import com.sentinelai.risk.RiskResult;

import java.util.List;

/** Risk waterfall: total score, severity, and the per-factor breakdown with reasons. */
public record RiskResponse(int score, Severity severity, List<FactorResult> breakdown) {

    public static RiskResponse from(Incident incident, ObjectMapper mapper) {
        if (incident.getRiskBreakdown() != null && !incident.getRiskBreakdown().isBlank()) {
            try {
                RiskResult r = mapper.readValue(incident.getRiskBreakdown(), RiskResult.class);
                return new RiskResponse(r.score(), r.severity(), r.breakdown());
            } catch (Exception ignored) {
                // fall through to the stored scalar values
            }
        }
        int score = incident.getRiskScore() == null ? 0 : incident.getRiskScore();
        return new RiskResponse(score, incident.getSeverity(), List.of());
    }
}
