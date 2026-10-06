package com.sentinelai.ai;

import com.sentinelai.ai.context.IncidentContext;
import com.sentinelai.ai.domain.ValidationStatus;
import com.sentinelai.ai.pipeline.AnalysisOutput;
import com.sentinelai.ai.validation.EvidenceValidator;
import com.sentinelai.ai.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The core guardrail: the model can only describe what the evidence supports. */
class EvidenceValidatorTest {

    private final EvidenceValidator validator = new EvidenceValidator(new AiProperties());

    private IncidentContext ctx() {
        var events = List.of(
                new IncidentContext.EventSummary(1L, "FAILED_LOGIN", "LOW", "203.0.113.5", "mallory",
                        "auth/login", "ua", "US", "2026-04-01T00:00:00Z"),
                new IncidentContext.EventSummary(2L, "FAILED_LOGIN", "LOW", "203.0.113.5", "mallory",
                        "auth/login", "ua", "US", "2026-04-01T00:00:10Z"));
        return new IncidentContext(10L, 1L, "HIGH", 80,
                new IncidentContext.Entity("user", "mallory"), List.of("T1110"),
                List.of(), events, List.of(), List.of(1L, 2L));
    }

    private AnalysisOutput.Recommendation goodRec() {
        return new AnalysisOutput.Recommendation("disable_user", "mallory", "reason");
    }

    @Test
    void validAnalysisWithCitedEvidence() {
        AnalysisOutput out = new AnalysisOutput("summary", 0.7, List.of("h"),
                List.of(goodRec()),
                List.of(new AnalysisOutput.Claim("two failed logins", List.of(1L, 2L))));
        ValidationResult r = validator.validate(out, ctx());
        assertThat(r.status()).isEqualTo(ValidationStatus.VALID);
        assertThat(r.faithfulness()).isEqualTo(1.0);
        assertThat(r.citedEventIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void fabricatedEventIdIsRejected() {
        AnalysisOutput out = new AnalysisOutput("s", 0.7, List.of(), List.of(goodRec()),
                List.of(new AnalysisOutput.Claim("cites a fake id", List.of(999L))));
        assertThat(validator.validate(out, ctx()).status()).isEqualTo(ValidationStatus.REJECTED);
    }

    @Test
    void uncitedClaimLowersFaithfulnessAndRejectsBelowThreshold() {
        AnalysisOutput out = new AnalysisOutput("s", 0.7, List.of(), List.of(goodRec()),
                List.of(new AnalysisOutput.Claim("uncited claim", List.of()),
                        new AnalysisOutput.Claim("another uncited", List.of())));
        ValidationResult r = validator.validate(out, ctx());
        assertThat(r.faithfulness()).isEqualTo(0.0);
        assertThat(r.status()).isEqualTo(ValidationStatus.REJECTED);
    }

    @Test
    void disallowedActionIsRejected() {
        AnalysisOutput out = new AnalysisOutput("s", 0.7, List.of(),
                List.of(new AnalysisOutput.Recommendation("delete_database", "mallory", "r")),
                List.of(new AnalysisOutput.Claim("c", List.of(1L))));
        assertThat(validator.validate(out, ctx()).status()).isEqualTo(ValidationStatus.REJECTED);
    }

    @Test
    void recommendationTargetNotInEvidenceIsRejected() {
        AnalysisOutput out = new AnalysisOutput("s", 0.7, List.of(),
                List.of(new AnalysisOutput.Recommendation("block_ip", "8.8.8.8", "r")),
                List.of(new AnalysisOutput.Claim("c", List.of(1L))));
        assertThat(validator.validate(out, ctx()).status()).isEqualTo(ValidationStatus.REJECTED);
    }

    @Test
    void missingSummaryIsRejected() {
        AnalysisOutput out = new AnalysisOutput("  ", 0.7, List.of(), List.of(),
                List.of(new AnalysisOutput.Claim("c", List.of(1L))));
        assertThat(validator.validate(out, ctx()).status()).isEqualTo(ValidationStatus.REJECTED);
    }
}
