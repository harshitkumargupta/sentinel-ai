package com.sentinelai.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.ai.context.IncidentContext;
import com.sentinelai.ai.local.LocalAnalysisEngine;
import com.sentinelai.ai.pipeline.AnalysisOutput;

/**
 * The default, keyless AI provider ({@code sentinel.ai.provider=local}). Investigations are answered
 * by the offline {@link LocalAnalysisEngine}; NL search and admin-risk prompts reuse the
 * deterministic {@link FakeLlmClient} logic. No network, no API key, and the same response schema
 * as a remote model, so it passes the same evidence validation.
 */
public class LocalLlmClient implements LlmClient {

    private final LocalAnalysisEngine engine;
    private final FakeLlmClient deterministic;
    private final ObjectMapper objectMapper;

    public LocalLlmClient(LocalAnalysisEngine engine, ObjectMapper objectMapper) {
        this.engine = engine;
        this.objectMapper = objectMapper;
        this.deterministic = new FakeLlmClient(objectMapper);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (!"investigation".equals(request.purpose())) {
            LlmResponse r = deterministic.complete(request);
            return new LlmResponse(r.content(), LocalAnalysisEngine.MODEL_NAME, r.promptTokens(),
                    r.completionTokens(), r.latencyMs());
        }
        long start = System.nanoTime();
        IncidentContext ctx = deterministic.extractContext(request.userPrompt());
        String content = ctx == null ? "{}" : toJson(engine.analyze(ctx));
        int promptTokens = Math.max(1, (request.systemPrompt().length() + request.userPrompt().length()) / 4);
        return new LlmResponse(content, LocalAnalysisEngine.MODEL_NAME, promptTokens,
                Math.max(1, content.length() / 4), (System.nanoTime() - start) / 1_000_000);
    }

    private String toJson(AnalysisOutput output) {
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            throw new LlmUnavailableException("local engine produced unserializable output", e);
        }
    }
}
