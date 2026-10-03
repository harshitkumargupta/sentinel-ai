package com.sentinelai.ai.domain;

/**
 * Review status of an AI analysis (human-in-the-loop). Order must match the
 * {@code ENUM(...)} in migration {@code V7__ai_analyses.sql}.
 */
public enum AnalysisStatus {
    PENDING,
    APPROVED,
    REJECTED,
    MODIFIED
}
