package com.sentinelai.ai.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.ai.domain.AiAnalysis;
import com.sentinelai.ai.pipeline.AnalysisOutput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** API DTOs for the AI investigation workflow (entities are never exposed at the boundary). */
public final class AiDtos {

    private AiDtos() {
    }

    public record InvestigateResponse(Long analysisId, boolean reused, String state) {
    }

    public record ReviewRequest(@NotNull Decision decision, String note) {
        public enum Decision { APPROVE, REJECT, MODIFY }
    }

    public record AnalysisResponse(
            Long id,
            Long incidentId,
            String state,            // QUEUED/RUNNING/COMPLETE/FAILED
            String validationStatus, // VALID/REJECTED/FALLBACK
            String reviewStatus,     // PENDING/APPROVED/REJECTED/MODIFIED
            BigDecimal confidence,
            BigDecimal faithfulness,
            boolean injectionDetected,
            String modelName,
            String templateVersion,
            Integer latencyMs,
            Integer promptTokens,
            Integer completionTokens,
            BigDecimal costUsd,
            String reviewNote,
            Instant createdAt,
            AnalysisOutput output) {

        public static AnalysisResponse from(AiAnalysis a, ObjectMapper mapper) {
            AnalysisOutput output = null;
            if (a.getOutput() != null) {
                try {
                    output = mapper.readValue(a.getOutput(), AnalysisOutput.class);
                } catch (Exception ignored) {
                    // tolerate legacy/non-structured output
                }
            }
            return new AnalysisResponse(
                    a.getId(),
                    a.getIncident().getId(),
                    a.getAnalysisState() == null ? null : a.getAnalysisState().name(),
                    a.getValidationStatus() == null ? null : a.getValidationStatus().name(),
                    a.getStatus() == null ? null : a.getStatus().name(),
                    a.getConfidence(),
                    a.getFaithfulnessScore(),
                    a.isInjectionDetected(),
                    a.getModelName(),
                    a.getTemplateVersion(),
                    a.getLatencyMs(),
                    a.getPromptTokens(),
                    a.getCompletionTokens(),
                    a.getCostUsd(),
                    a.getReviewNote(),
                    a.getCreatedAt(),
                    output);
        }
    }
}
