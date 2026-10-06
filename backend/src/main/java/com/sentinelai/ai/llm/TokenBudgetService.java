package com.sentinelai.ai.llm;

import com.sentinelai.ai.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks daily LLM token and cost usage globally, and token usage per org, resetting at UTC
 * midnight. Over budget/quota → the guarded client falls back instead of calling the model.
 * In-memory and best-effort (single-instance dev); a multi-instance deployment would move this to a
 * shared store.
 */
@Service
@RequiredArgsConstructor
public class TokenBudgetService {

    private final AiProperties props;
    private final Clock clock;

    private volatile LocalDate day = LocalDate.MIN;
    private final AtomicLong globalTokens = new AtomicLong();
    private final AtomicLong globalCostMicros = new AtomicLong(); // USD * 1_000_000
    private final Map<Long, AtomicLong> orgTokens = new ConcurrentHashMap<>();

    private synchronized void rollIfNeeded() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (!today.equals(day)) {
            day = today;
            globalTokens.set(0);
            globalCostMicros.set(0);
            orgTokens.clear();
        }
    }

    /** True if there is remaining global budget and per-org quota to attempt a call. */
    public boolean canSpend(Long orgId) {
        rollIfNeeded();
        AiProperties.Budget b = props.getBudget();
        if (globalTokens.get() >= b.getDailyTokenBudget()) {
            return false;
        }
        if (globalCostMicros.get() >= (long) (b.getDailyCostBudgetUsd() * 1_000_000)) {
            return false;
        }
        long used = orgId == null ? 0 : orgTokens.getOrDefault(orgId, new AtomicLong()).get();
        return used < b.getPerOrgDailyTokenQuota();
    }

    /** Record usage after a successful call and return the call's cost in USD. */
    public double record(Long orgId, int promptTokens, int completionTokens) {
        rollIfNeeded();
        AiProperties.Budget b = props.getBudget();
        double cost = promptTokens / 1000.0 * b.getCostPer1kPromptUsd()
                + completionTokens / 1000.0 * b.getCostPer1kCompletionUsd();
        globalTokens.addAndGet(promptTokens + (long) completionTokens);
        globalCostMicros.addAndGet((long) (cost * 1_000_000));
        if (orgId != null) {
            orgTokens.computeIfAbsent(orgId, k -> new AtomicLong())
                    .addAndGet(promptTokens + (long) completionTokens);
        }
        return cost;
    }

    public long globalTokensUsed() {
        rollIfNeeded();
        return globalTokens.get();
    }
}
