package com.sentinelai.ai.assistant;

import com.sentinelai.ai.context.IncidentContext;

/**
 * Answers analyst questions about one incident. The default implementation is the offline
 * {@link LocalIncidentAssistant}; an LLM-backed implementation can be registered later as a
 * {@code @Primary} bean without touching the controller.
 */
public interface IncidentAssistant {

    /** {@code intent} is null when the question matched none of the supported intents. */
    AssistantAnswer answer(IncidentContext ctx, AskIntent intent, String question);
}
