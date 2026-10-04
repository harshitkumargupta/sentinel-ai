package com.sentinelai.ai.llm;

import com.sentinelai.ai.AiProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wraps the real {@link LlmClient} with the operational guardrails: a circuit breaker, a daily
 * token/cost budget and per-org quota, retries with backoff, and per-call metrics (model, latency,
 * tokens, cost). Any guard trip or transport failure surfaces as {@link LlmUnavailableException},
 * which the pipeline treats as a fallback signal — never a server error.
 */
@Slf4j
@Component
@Primary
public class GuardedLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final AiProperties props;
    private final TokenBudgetService budget;
    private final MeterRegistry meters;
    private final Clock clock;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long circuitOpenUntil = 0;

    public GuardedLlmClient(@Qualifier("llmDelegate") LlmClient delegate, AiProperties props,
                            TokenBudgetService budget, MeterRegistry meters, Clock clock) {
        this.delegate = delegate;
        this.props = props;
        this.budget = budget;
        this.meters = meters;
        this.clock = clock;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (circuitOpen()) {
            meters.counter("sentinel.ai.llm.calls", "outcome", "circuit_open").increment();
            throw new LlmUnavailableException("LLM circuit is open");
        }
        if (!budget.canSpend(request.orgId())) {
            meters.counter("sentinel.ai.llm.calls", "outcome", "budget_exceeded").increment();
            throw new LlmUnavailableException("AI budget/quota exceeded");
        }

        LlmUnavailableException last = null;
        for (int attempt = 0; attempt <= props.getMaxRetries(); attempt++) {
            try {
                LlmResponse resp = delegate.complete(request);
                double cost = budget.record(request.orgId(), resp.promptTokens(), resp.completionTokens());
                consecutiveFailures.set(0);
                meters.counter("sentinel.ai.llm.calls", "outcome", "ok").increment();
                meters.counter("sentinel.ai.llm.tokens").increment(resp.totalTokens());
                meters.timer("sentinel.ai.llm.latency").record(java.time.Duration.ofMillis(resp.latencyMs()));
                log.debug("LLM ok purpose={} model={} tokens={} latencyMs={} costUsd={}",
                        request.purpose(), resp.modelName(), resp.totalTokens(), resp.latencyMs(), cost);
                return resp;
            } catch (LlmUnavailableException e) {
                last = e;
                if (attempt < props.getMaxRetries()) {
                    sleep(props.getBackoffMs() * (1L << attempt));
                }
            }
        }
        onFailure();
        meters.counter("sentinel.ai.llm.calls", "outcome", "fail").increment();
        throw last != null ? last : new LlmUnavailableException("LLM failed");
    }

    private boolean circuitOpen() {
        return clock.millis() < circuitOpenUntil;
    }

    private void onFailure() {
        int fails = consecutiveFailures.incrementAndGet();
        if (fails >= props.getCircuitBreaker().getFailureThreshold()) {
            circuitOpenUntil = clock.millis() + props.getCircuitBreaker().getResetSeconds() * 1000L;
            consecutiveFailures.set(0);
            log.warn("LLM circuit opened for {}s after {} consecutive failures",
                    props.getCircuitBreaker().getResetSeconds(), fails);
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
