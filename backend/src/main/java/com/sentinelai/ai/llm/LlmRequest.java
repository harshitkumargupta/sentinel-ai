package com.sentinelai.ai.llm;

/**
 * A single completion request. The system prompt carries only trusted instructions; all
 * attacker-controlled data lives inside the (already-sanitized, delimited) user prompt.
 *
 * @param orgId      the requesting org (for per-org budget accounting)
 * @param purpose    short label for metrics/logging (e.g. "investigation", "nl-search")
 */
public record LlmRequest(Long orgId, String purpose, String systemPrompt, String userPrompt,
                         int maxTokens) {
}
