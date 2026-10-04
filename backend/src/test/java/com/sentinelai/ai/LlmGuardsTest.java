package com.sentinelai.ai;

import com.sentinelai.ai.llm.GuardedLlmClient;
import com.sentinelai.ai.llm.LlmClient;
import com.sentinelai.ai.llm.LlmRequest;
import com.sentinelai.ai.llm.LlmResponse;
import com.sentinelai.ai.llm.LlmUnavailableException;
import com.sentinelai.ai.llm.TokenBudgetService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Token budget accounting and the guarded client (circuit breaker, budget gate, retries). */
class LlmGuardsTest {

    private final Clock clock = Clock.systemUTC();

    private AiProperties props() {
        AiProperties p = new AiProperties();
        p.setMaxRetries(1);
        p.setBackoffMs(1);
        return p;
    }

    @Test
    void budgetBlocksOnceDailyTokenBudgetIsExceeded() {
        AiProperties p = props();
        p.getBudget().setDailyTokenBudget(100);
        TokenBudgetService budget = new TokenBudgetService(p, clock);
        assertThat(budget.canSpend(1L)).isTrue();
        budget.record(1L, 80, 40); // 120 > 100
        assertThat(budget.canSpend(1L)).isFalse();
    }

    @Test
    void perOrgQuotaIsEnforced() {
        AiProperties p = props();
        p.getBudget().setPerOrgDailyTokenQuota(50);
        TokenBudgetService budget = new TokenBudgetService(p, clock);
        budget.record(7L, 40, 20); // 60 > 50 for org 7
        assertThat(budget.canSpend(7L)).isFalse();
        assertThat(budget.canSpend(8L)).isTrue(); // other org unaffected
    }

    @Test
    void budgetExceededShortCircuitsWithoutCallingModel() {
        AiProperties p = props();
        p.getBudget().setDailyTokenBudget(0);
        AtomicInteger calls = new AtomicInteger();
        LlmClient delegate = req -> {
            calls.incrementAndGet();
            return new LlmResponse("{}", "m", 1, 1, 1);
        };
        var guarded = new GuardedLlmClient(delegate, p, new TokenBudgetService(p, clock),
                new SimpleMeterRegistry(), clock);
        assertThatThrownBy(() -> guarded.complete(req())).isInstanceOf(LlmUnavailableException.class);
        assertThat(calls.get()).isZero();
    }

    @Test
    void retriesThenOpensCircuitAfterRepeatedFailures() {
        AiProperties p = props();
        p.getCircuitBreaker().setFailureThreshold(2);
        AtomicInteger calls = new AtomicInteger();
        LlmClient failing = req -> {
            calls.incrementAndGet();
            throw new LlmUnavailableException("boom");
        };
        var guarded = new GuardedLlmClient(failing, p, new TokenBudgetService(p, clock),
                new SimpleMeterRegistry(), clock);

        // Each complete() does 1 + maxRetries(1) = 2 delegate calls and one failure increment.
        assertThatThrownBy(() -> guarded.complete(req())).isInstanceOf(LlmUnavailableException.class);
        assertThatThrownBy(() -> guarded.complete(req())).isInstanceOf(LlmUnavailableException.class);
        int before = calls.get();
        // Circuit now open: the next call must not reach the delegate.
        assertThatThrownBy(() -> guarded.complete(req()))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("circuit");
        assertThat(calls.get()).isEqualTo(before);
    }

    @Test
    void successRecordsTokens() {
        AiProperties p = props();
        TokenBudgetService budget = new TokenBudgetService(p, clock);
        LlmClient ok = req -> new LlmResponse("{\"x\":1}", "m", 10, 5, 2);
        var guarded = new GuardedLlmClient(ok, p, budget, new SimpleMeterRegistry(), clock);
        LlmResponse r = guarded.complete(req());
        assertThat(r.totalTokens()).isEqualTo(15);
        assertThat(budget.globalTokensUsed()).isEqualTo(15);
    }

    private LlmRequest req() {
        return new LlmRequest(1L, "test", "sys", "user", 100);
    }
}
