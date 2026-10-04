package com.sentinelai.ai.llm;

/** The model's reply plus the accounting we record on every call. */
public record LlmResponse(String content, String modelName, int promptTokens, int completionTokens,
                          long latencyMs) {

    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}
