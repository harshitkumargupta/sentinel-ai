package com.sentinelai.ai.llm;

/**
 * Seam for a large-language-model completion. Implementations: {@code FakeLlmClient} (deterministic,
 * used in tests/CI and as the provider default) and {@code HttpLlmClient} (OpenAI-compatible HTTP).
 * The model has no tools and no execution ability — it only returns text.
 */
public interface LlmClient {

    /**
     * @throws LlmUnavailableException on timeout, transport error, an open circuit or an exhausted
     *         budget — callers treat this as a signal to fall back, never as a server error.
     */
    LlmResponse complete(LlmRequest request);
}
