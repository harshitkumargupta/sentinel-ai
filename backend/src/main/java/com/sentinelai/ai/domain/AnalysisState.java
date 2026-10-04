package com.sentinelai.ai.domain;

/**
 * Async lifecycle of an investigation (distinct from the human review {@link AnalysisStatus}).
 * Order must match the {@code ENUM(...)} in migration {@code V24__ai_investigation.sql}.
 */
public enum AnalysisState {
    QUEUED,
    RUNNING,
    COMPLETE,
    FAILED
}
