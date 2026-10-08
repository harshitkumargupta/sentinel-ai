package com.sentinelai.ai.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The schema the LLM must return. Any deviation (malformed JSON, missing fields, extra prose) is a
 * validation failure. Unknown properties are ignored so trailing model chatter can't break parsing,
 * but the structure and the evidence are strictly validated afterwards.
 *
 * <p>{@code report} is optional: the offline engine always fills it (what happened, timeline,
 * affected entities, MITRE mapping, severity reasoning, next steps); a remote model may omit it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisOutput(
        String summary,
        double confidence,
        List<String> hypotheses,
        List<Recommendation> recommendations,
        List<Claim> claims,
        Report report) {

    public AnalysisOutput(String summary, double confidence, List<String> hypotheses,
                          List<Recommendation> recommendations, List<Claim> claims) {
        this(summary, confidence, hypotheses, recommendations, claims, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recommendation(String action, String target, String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Claim(String text, List<Long> evidenceEventIds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Report(
            String whatHappened,
            List<TimelineEntry> timeline,
            Affected affected,
            List<MitreMapping> mitre,
            String severityReasoning,
            List<String> nextSteps) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimelineEntry(String at, String description, List<Long> eventIds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Affected(List<String> users, List<String> ips, List<String> hosts, List<String> countries) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MitreMapping(String id, String name, String tactic) {
    }
}
