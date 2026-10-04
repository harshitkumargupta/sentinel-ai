package com.sentinelai.ai.context;

import java.util.List;

/**
 * Output of {@link IncidentContextBuilder}: the model-facing context, a stable hash of it (for
 * idempotent re-investigation), and any prompt-injection snippets found in the event data.
 */
public record BuiltContext(IncidentContext context, String contextHash, List<String> injectionHits) {

    public boolean injectionDetected() {
        return injectionHits != null && !injectionHits.isEmpty();
    }
}
