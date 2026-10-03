package com.sentinelai.ai.domain;

/**
 * Outcome of validating an AI agent's output against the evidence/guardrails.
 * Order must match the {@code ENUM(...)} in migration {@code V9__ai_analyses.sql}.
 */
public enum ValidationStatus {
    VALID,
    REJECTED,
    FALLBACK
}
