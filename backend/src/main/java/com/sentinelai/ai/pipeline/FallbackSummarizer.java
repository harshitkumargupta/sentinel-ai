package com.sentinelai.ai.pipeline;

import com.sentinelai.ai.context.IncidentContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Produces a deterministic analysis from the risk breakdown and timeline, with no LLM. Used whenever
 * AI is disabled, the model is unavailable/over budget, or its output fails validation. Its claims
 * cite real events and its recommendations target entities present in the evidence, so it always
 * passes the same guardrails — it is just labeled FALLBACK.
 */
@Component
public class FallbackSummarizer {

    public AnalysisOutput summarize(IncidentContext ctx) {
        String entityType = ctx.entity() == null ? "unknown" : ctx.entity().type();
        String entityValue = ctx.entity() == null ? null : ctx.entity().value();
        String topType = ctx.events().isEmpty() ? "activity" : String.valueOf(ctx.events().get(0).type());

        String factors = ctx.riskFactors().stream()
                .map(f -> f.name() + " (+" + f.points() + ")")
                .collect(Collectors.joining(", "));
        if (factors.isEmpty()) {
            factors = "no elevated factors";
        }
        String mitre = ctx.mitre().isEmpty() ? "none" : String.join(", ", ctx.mitre());

        String summary = ("Deterministic summary (no AI): incident #%d, severity %s, risk %s. "
                + "%d event(s) centered on %s '%s'. Risk factors: %s. MITRE: %s.")
                .formatted(ctx.incidentId(), ctx.severity(), String.valueOf(ctx.riskScore()),
                        ctx.eventIds().size(), entityType, entityValue, factors, mitre);

        List<String> hypotheses = new ArrayList<>();
        if (!ctx.riskFactors().isEmpty()) {
            hypotheses.add("Elevated risk driven by: " + factors + ".");
        }
        hypotheses.add("Repeated " + topType + " involving " + entityValue
                + " is consistent with malicious rather than benign activity.");

        List<AnalysisOutput.Recommendation> recs = new ArrayList<>();
        if ("ip".equals(entityType) && entityValue != null) {
            recs.add(new AnalysisOutput.Recommendation("block_ip", entityValue,
                    "Source IP is the common factor across the cited events."));
        } else if ("user".equals(entityType) && entityValue != null) {
            recs.add(new AnalysisOutput.Recommendation("disable_user", entityValue,
                    "Account is the target across the cited events; disable pending review."));
        }
        if (entityValue != null) {
            recs.add(new AnalysisOutput.Recommendation("monitor", entityValue,
                    "Continue monitoring the entity for further activity."));
        }

        List<AnalysisOutput.Claim> claims = List.of(new AnalysisOutput.Claim(
                "%d events were recorded for this incident.".formatted(ctx.eventIds().size()),
                ctx.eventIds()));

        return new AnalysisOutput(summary, 0.3, hypotheses, recs, claims);
    }
}
