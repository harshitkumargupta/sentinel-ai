package com.sentinelai.ai.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The schema the LLM must return. Any deviation (malformed JSON, missing fields, extra prose) is a
 * validation failure. Unknown properties are ignored so trailing model chatter can't break parsing,
 * but the structure and the evidence are strictly validated afterwards.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisOutput(
        String summary,
        double confidence,
        List<String> hypotheses,
        List<Recommendation> recommendations,
        List<Claim> claims) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recommendation(String action, String target, String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Claim(String text, List<Long> evidenceEventIds) {
    }
}
