package com.sentinelai.detection.engine;

/**
 * Carries the current simulator run id across the synchronous ingest→detection call on one thread,
 * so alerts produced while ingesting simulated events can be tagged with their run for evaluation.
 */
public final class RunContextHolder {

    private static final ThreadLocal<String> CURRENT_RUN = new ThreadLocal<>();

    private RunContextHolder() {
    }

    public static void set(String runId) {
        CURRENT_RUN.set(runId);
    }

    public static String get() {
        return CURRENT_RUN.get();
    }

    public static void clear() {
        CURRENT_RUN.remove();
    }
}
