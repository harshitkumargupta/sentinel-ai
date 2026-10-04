package com.sentinelai.ai.domain;

/**
 * Type of AI agent that produced an analysis. Order must match the {@code ENUM(...)}
 * in migration {@code V7__ai_analyses.sql}.
 */
public enum AgentType {
    THREAT_ANALYSIS,
    CORRELATION,
    ROOT_CAUSE,
    RESPONSE_RECOMMENDATION,
    /** Phase 12: the single evidence-validated investigation covering all three stages. */
    INVESTIGATION
}
