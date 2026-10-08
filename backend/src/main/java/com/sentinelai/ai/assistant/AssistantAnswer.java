package com.sentinelai.ai.assistant;

import java.util.List;

/** One answer from the incident assistant, always grounded in the incident's own events. */
public record AssistantAnswer(
        String intent,
        String question,
        String answer,
        List<String> bullets,
        List<Long> evidenceEventIds,
        String engine,
        boolean offline) {
}
