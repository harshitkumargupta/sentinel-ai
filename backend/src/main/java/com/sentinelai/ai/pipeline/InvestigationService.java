package com.sentinelai.ai.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.ai.AiProperties;
import com.sentinelai.ai.context.BuiltContext;
import com.sentinelai.ai.context.IncidentContext;
import com.sentinelai.ai.context.IncidentContextBuilder;
import com.sentinelai.ai.domain.AgentType;
import com.sentinelai.ai.domain.AiAnalysis;
import com.sentinelai.ai.domain.AnalysisState;
import com.sentinelai.ai.domain.AnalysisStatus;
import com.sentinelai.ai.domain.ValidationStatus;
import com.sentinelai.ai.llm.LlmClient;
import com.sentinelai.ai.llm.LlmRequest;
import com.sentinelai.ai.llm.LlmResponse;
import com.sentinelai.ai.llm.LlmUnavailableException;
import com.sentinelai.ai.prompt.PromptTemplates;
import com.sentinelai.ai.repository.AiAnalysisRepository;
import com.sentinelai.ai.security.InjectionFlagService;
import com.sentinelai.ai.security.PromptSanitizer;
import com.sentinelai.ai.validation.EvidenceValidator;
import com.sentinelai.ai.validation.ValidationResult;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.RateLimitException;
import com.sentinelai.incident.correlation.TimelineService;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.ratelimit.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the evidence-validated incident investigation: build a capped context, call the LLM
 * (analyze → correlate → recommend in one structured response), validate every claim and
 * recommendation against the evidence (one repair retry), and fall back to a deterministic summary
 * on any failure. Everything is gated by {@code ai.enabled}; off/unavailable always yields FALLBACK.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvestigationService {

    private record Outcome(AnalysisOutput output, ValidationStatus status, double faithfulness,
                           List<Long> citedIds, LlmResponse llm) {
    }

    private final AiProperties props;
    private final AiAnalysisRepository analysisRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentContextBuilder contextBuilder;
    private final LlmClient llm;
    private final PromptTemplates templates;
    private final PromptSanitizer sanitizer;
    private final EvidenceValidator validator;
    private final FallbackSummarizer fallback;
    private final InjectionFlagService injectionFlag;
    private final TimelineService timeline;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;
    private final RateLimiter rateLimiter;

    public record RequestResult(Long analysisId, boolean reused) {
    }

    /**
     * Rate-limited, idempotent request to investigate an incident. Returns an existing completed
     * analysis for the same incident+context (idempotent re-investigate), or creates a QUEUED one.
     */
    @Transactional
    public RequestResult request(Long incidentId, AppUserPrincipal actor) {
        Incident incident = incidentRepository.findById(incidentId)
                .filter(i -> i.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new com.sentinelai.common.exception.NotFoundException(
                        "Incident not found: " + incidentId));

        RateLimiter.Result rl = rateLimiter.tryAcquire(
                "ai:investigate:" + actor.getOrgId(),
                props.getRateLimitPerMinute(), props.getRateLimitPerMinute() / 60.0);
        if (!rl.allowed()) {
            throw new RateLimitException("Too many investigations; retry in " + rl.retryAfterSeconds() + "s");
        }

        BuiltContext built = contextBuilder.build(incident);
        var existing = analysisRepository.findFirstByIncident_IdAndContextHashAndAnalysisStateOrderByIdDesc(
                incident.getId(), built.contextHash(), AnalysisState.COMPLETE);
        if (existing.isPresent()) {
            return new RequestResult(existing.get().getId(), true);
        }

        AiAnalysis analysis = analysisRepository.save(AiAnalysis.builder()
                .incident(incident)
                .agentType(AgentType.INVESTIGATION)
                .analysisState(AnalysisState.QUEUED)
                .status(AnalysisStatus.PENDING)
                .templateVersion(templates.version())
                .contextHash(built.contextHash())
                .injectionDetected(false)
                .build());
        return new RequestResult(analysis.getId(), false);
    }

    /** Runs the pipeline for a queued analysis. Invoked by the thread-pool or Kafka worker. */
    @Transactional
    public void run(Long analysisId) {
        AiAnalysis analysis = analysisRepository.findById(analysisId).orElse(null);
        if (analysis == null || analysis.getAnalysisState() == AnalysisState.COMPLETE) {
            return; // idempotent: nothing to do
        }
        analysis.setAnalysisState(AnalysisState.RUNNING);
        analysisRepository.save(analysis);

        Incident incident = incidentRepository.findById(analysis.getIncident().getId()).orElseThrow();
        BuiltContext built = contextBuilder.build(incident);
        IncidentContext ctx = built.context();

        if (built.injectionDetected()) {
            injectionFlag.flag(incident, built.injectionHits());
            analysis.setInjectionDetected(true);
        }

        Outcome outcome = analyze(ctx);

        analysis.setContextHash(built.contextHash());
        analysis.setTemplateVersion(templates.version());
        analysis.setOutput(toJson(outcome.output()));
        analysis.setConfidence(BigDecimal.valueOf(outcome.output().confidence()));
        analysis.setValidationStatus(outcome.status());
        analysis.setFaithfulnessScore(BigDecimal.valueOf(round2(outcome.faithfulness())));
        analysis.setEvidenceEventIds(toJson(outcome.citedIds()));
        if (outcome.llm() != null) {
            analysis.setModelName(outcome.llm().modelName());
            analysis.setLatencyMs((int) outcome.llm().latencyMs());
            analysis.setPromptTokens(outcome.llm().promptTokens());
            analysis.setCompletionTokens(outcome.llm().completionTokens());
            analysis.setCostUsd(BigDecimal.valueOf(cost(outcome.llm())));
        } else {
            analysis.setModelName("deterministic");
            analysis.setLatencyMs(0);
        }
        analysis.setAnalysisState(AnalysisState.COMPLETE);
        analysisRepository.save(analysis);

        timeline.record(incident.getId(), "AI_ANALYSIS", "ai",
                "{\"status\":\"" + outcome.status() + "\",\"faithfulness\":" + round2(outcome.faithfulness()) + "}");
        meters.counter("sentinel.ai.analysis", "status", outcome.status().name()).increment();
        log.info("Investigation {} complete: status={} faithfulness={} injection={}",
                analysisId, outcome.status(), round2(outcome.faithfulness()), analysis.isInjectionDetected());
    }

    private Outcome analyze(IncidentContext ctx) {
        if (!props.isEnabled()) {
            return fallbackOutcome(ctx);
        }
        try {
            String system = templates.load("investigation.system");
            String data = sanitizer.wrapData(toJson(ctx));
            String user = cap(templates.render("investigation.user", Map.of("data", data)));
            LlmRequest req = new LlmRequest(ctx.orgId(), "investigation", system, user, props.getMaxTokens());

            Outcome first = attempt(ctx, req);
            if (first != null) {
                return first;
            }
            // One repair retry with explicit feedback.
            String repairUser = cap(user + "\n\nYour previous response was invalid. "
                    + "Return ONLY valid JSON matching the schema, citing only event ids from the list "
                    + "and targeting only IPs/users present in the data.");
            Outcome repaired = attempt(ctx, new LlmRequest(ctx.orgId(), "investigation", system,
                    repairUser, props.getMaxTokens()));
            if (repaired != null) {
                return repaired;
            }
            meters.counter("sentinel.ai.analysis", "status", "rejected_to_fallback").increment();
            return fallbackOutcome(ctx);
        } catch (LlmUnavailableException e) {
            log.info("LLM unavailable ({}); falling back to deterministic analysis", e.getMessage());
            return fallbackOutcome(ctx);
        }
    }

    /** One LLM call + parse + validate; returns a VALID Outcome or null to trigger repair/fallback. */
    private Outcome attempt(IncidentContext ctx, LlmRequest req) {
        LlmResponse resp = llm.complete(req);
        AnalysisOutput out = parse(resp.content());
        if (out == null) {
            return null;
        }
        ValidationResult v = validator.validate(out, ctx);
        if (v.valid()) {
            return new Outcome(out, ValidationStatus.VALID, v.faithfulness(), v.citedEventIds(), resp);
        }
        return null;
    }

    private Outcome fallbackOutcome(IncidentContext ctx) {
        AnalysisOutput out = fallback.summarize(ctx);
        ValidationResult v = validator.validate(out, ctx);
        return new Outcome(out, ValidationStatus.FALLBACK, v.faithfulness(), v.citedEventIds(), null);
    }

    private AnalysisOutput parse(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String json = content.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readValue(json.substring(start, end + 1), AnalysisOutput.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String cap(String s) {
        int max = props.getContext().getMaxPromptChars();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private double cost(LlmResponse r) {
        AiProperties.Budget b = props.getBudget();
        return r.promptTokens() / 1000.0 * b.getCostPer1kPromptUsd()
                + r.completionTokens() / 1000.0 * b.getCostPer1kCompletionUsd();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "null";
        }
    }
}
