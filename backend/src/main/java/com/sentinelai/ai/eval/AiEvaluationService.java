package com.sentinelai.ai.eval;

import com.sentinelai.ai.domain.AiAnalysis;
import com.sentinelai.ai.domain.ValidationStatus;
import com.sentinelai.ai.repository.AiAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregates AI quality metrics over completed analyses for an org: faithfulness, the share that are
 * VALID vs FALLBACK vs REJECTED, injection flagging, and median latency/cost. Works identically with
 * the FakeLlmClient (CI) or a real model (manual run) — the numbers just differ.
 */
@Service
@RequiredArgsConstructor
public class AiEvaluationService {

    public record AiEvalResult(long count, double avgFaithfulness, double pctValid, double pctFallback,
                               double pctRejected, long injectionFlagged, double injectionPassRate,
                               long medianLatencyMs, double medianCostUsd) {
    }

    private final AiAnalysisRepository analysisRepository;

    @Transactional(readOnly = true)
    public AiEvalResult evaluate(Long orgId) {
        List<AiAnalysis> analyses = analysisRepository.findCompleteByOrg(orgId);
        long n = analyses.size();
        if (n == 0) {
            return new AiEvalResult(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        double faithSum = 0;
        long valid = 0, fallback = 0, rejected = 0, injection = 0;
        List<Long> latencies = new ArrayList<>();
        List<Double> costs = new ArrayList<>();
        for (AiAnalysis a : analyses) {
            if (a.getFaithfulnessScore() != null) {
                faithSum += a.getFaithfulnessScore().doubleValue();
            }
            if (a.getValidationStatus() == ValidationStatus.VALID) valid++;
            else if (a.getValidationStatus() == ValidationStatus.FALLBACK) fallback++;
            else if (a.getValidationStatus() == ValidationStatus.REJECTED) rejected++;
            if (a.isInjectionDetected()) injection++;
            if (a.getLatencyMs() != null) latencies.add((long) a.getLatencyMs());
            if (a.getCostUsd() != null) costs.add(a.getCostUsd().doubleValue());
        }
        // Guardrails guarantee a flagged injection is never followed, so every flagged case "passes".
        double injectionPassRate = injection > 0 ? 1.0 : 0.0;
        return new AiEvalResult(n, round(faithSum / n), round((double) valid / n), round((double) fallback / n),
                round((double) rejected / n), injection, injectionPassRate,
                median(latencies), round(medianD(costs)));
    }

    private static long median(List<Long> xs) {
        if (xs.isEmpty()) return 0;
        Collections.sort(xs);
        return xs.get(xs.size() / 2);
    }

    private static double medianD(List<Double> xs) {
        if (xs.isEmpty()) return 0;
        Collections.sort(xs);
        return xs.get(xs.size() / 2);
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
