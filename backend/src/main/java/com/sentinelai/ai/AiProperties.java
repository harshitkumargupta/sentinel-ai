package com.sentinelai.ai;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AI investigation configuration. Disabled by default — with {@code sentinel.ai.enabled=false} every
 * AI feature degrades to a deterministic fallback and no LLM is ever contacted. The API key is read
 * only from the {@code LLM_API_KEY} environment variable (never a property, never logged).
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.ai")
public class AiProperties {

    /** Master feature flag for all AI features. */
    private boolean enabled = false;

    /** {@code fake} (deterministic, for tests/dev) or {@code http} (OpenAI-compatible endpoint). */
    @NotBlank
    private String provider = "fake";

    @NotBlank
    private String model = "fake-model";

    /** OpenAI-compatible base URL (chat completions at {baseUrl}/chat/completions). */
    @NotBlank
    private String baseUrl = "https://api.openai.com/v1";

    @Min(100)
    private int timeoutMs = 20_000;

    @Min(64)
    private int maxTokens = 1_200;

    @Min(0)
    private int maxRetries = 2;

    @Min(1)
    private long backoffMs = 500;

    /** Prompt template version recorded on every analysis. */
    @NotBlank
    private String templateVersion = "v1";

    /** Investigations allowed per org per minute. */
    @Min(1)
    private int rateLimitPerMinute = 10;

    /** Minimum faithfulness (supported claims / total) for an analysis to be accepted as VALID. */
    private double minFaithfulness = 0.5;

    private final CircuitBreaker circuitBreaker = new CircuitBreaker();
    private final Budget budget = new Budget();
    private final Redaction redaction = new Redaction();
    private final ContextCaps context = new ContextCaps();
    private final NlSearch nlSearch = new NlSearch();

    @Getter
    @Setter
    public static class CircuitBreaker {
        @Min(1) private int failureThreshold = 5;
        @Min(1) private int resetSeconds = 60;
    }

    /** Daily token/cost budgets (global) and a per-org daily token quota. Exceeding → fallback. */
    @Getter
    @Setter
    public static class Budget {
        @Min(0) private long dailyTokenBudget = 2_000_000;
        private double dailyCostBudgetUsd = 25.0;
        @Min(0) private long perOrgDailyTokenQuota = 500_000;
        private double costPer1kPromptUsd = 0.003;
        private double costPer1kCompletionUsd = 0.015;
    }

    @Getter
    @Setter
    public static class Redaction {
        private boolean maskEmails = true;
        private boolean maskIps = false; // IPs are often the evidence; mask only if required
    }

    /** Caps so we never send unbounded context to the model. */
    @Getter
    @Setter
    public static class ContextCaps {
        @Min(1) private int maxEvents = 50;
        @Min(1) private int maxTimelineEntries = 30;
        @Min(16) private int maxFieldChars = 512;
        @Min(500) private int maxPromptChars = 24_000;
    }

    @Getter
    @Setter
    public static class NlSearch {
        @Min(1) private int maxLimit = 200;
        @Min(1) private int maxRangeDays = 90;
    }
}
