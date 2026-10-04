package com.sentinelai.ai.validation;

import com.sentinelai.ai.domain.ValidationStatus;

import java.util.List;

/**
 * Outcome of validating an LLM analysis against the evidence and guardrails.
 *
 * @param status          VALID or REJECTED (FALLBACK is applied by the pipeline, not here)
 * @param faithfulness    supported claims / total claims (0.0–1.0)
 * @param citedEventIds   the distinct, validated event ids cited across all claims
 * @param reasons         why it was rejected (empty when VALID)
 */
public record ValidationResult(ValidationStatus status, double faithfulness, int supportedClaims,
                               int totalClaims, List<Long> citedEventIds, List<String> reasons) {

    public boolean valid() {
        return status == ValidationStatus.VALID;
    }
}
