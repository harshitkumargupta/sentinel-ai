package com.sentinelai.ai.validation;

import com.sentinelai.ai.AiProperties;
import com.sentinelai.ai.context.IncidentContext;
import com.sentinelai.ai.domain.ValidationStatus;
import com.sentinelai.ai.pipeline.AnalysisOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates an LLM analysis against the incident's evidence and the guardrails. This is the core
 * safety control: the model can only ever describe what the evidence supports.
 *
 * <p>Hard rejections (→ repair retry, then deterministic fallback):
 * <ul>
 *   <li>missing/invalid schema (no summary, confidence out of 0–1),</li>
 *   <li>a claim citing an event id that is not part of this incident (fabricated/foreign),</li>
 *   <li>a recommendation whose action is not on the allow-list,</li>
 *   <li>a recommendation whose target is not an IP/user present in the evidence,</li>
 *   <li>faithfulness below the configured minimum.</li>
 * </ul>
 * Uncited claims are not fabrications but count as unsupported, lowering the faithfulness score.
 */
@Component
@RequiredArgsConstructor
public class EvidenceValidator {

    /** The only response actions the model may propose. */
    public static final Set<String> ALLOWED_ACTIONS =
            Set.of("block_ip", "disable_user", "force_password_reset", "isolate_host", "monitor");

    private final AiProperties props;

    public ValidationResult validate(AnalysisOutput output, IncidentContext ctx) {
        List<String> reasons = new java.util.ArrayList<>();

        if (output == null || output.summary() == null || output.summary().isBlank()) {
            return reject(List.of("missing summary"));
        }
        if (output.confidence() < 0 || output.confidence() > 1) {
            reasons.add("confidence out of range");
        }

        Set<Long> allowedIds = new LinkedHashSet<>(ctx.eventIds());
        Set<String> evidenceTargets = evidenceTargets(ctx);

        // Validate recommendations: allow-listed actions, targets present in the evidence.
        if (output.recommendations() != null) {
            for (AnalysisOutput.Recommendation r : output.recommendations()) {
                if (r == null || r.action() == null || !ALLOWED_ACTIONS.contains(r.action())) {
                    reasons.add("disallowed action: " + (r == null ? "null" : r.action()));
                    continue;
                }
                if (r.target() == null || !evidenceTargets.contains(r.target())) {
                    reasons.add("recommendation target not in evidence: " + (r == null ? "null" : r.target()));
                }
            }
        }

        // Validate claims: every cited id must belong to this incident.
        Set<Long> citedValid = new LinkedHashSet<>();
        int total = output.claims() == null ? 0 : output.claims().size();
        int supported = 0;
        if (output.claims() != null) {
            for (AnalysisOutput.Claim c : output.claims()) {
                List<Long> ids = c == null ? null : c.evidenceEventIds();
                if (ids == null || ids.isEmpty()) {
                    continue; // uncited → unsupported (not a hard failure)
                }
                boolean allValid = true;
                for (Long id : ids) {
                    if (id == null || !allowedIds.contains(id)) {
                        reasons.add("fabricated/foreign event id cited: " + id);
                        allValid = false;
                    } else {
                        citedValid.add(id);
                    }
                }
                if (allValid) {
                    supported++;
                }
            }
        }

        double faithfulness = total == 0 ? 0.0 : (double) supported / total;

        if (!reasons.isEmpty()) {
            return new ValidationResult(ValidationStatus.REJECTED, faithfulness, supported, total,
                    List.copyOf(citedValid), reasons);
        }
        if (total == 0 || faithfulness < props.getMinFaithfulness()) {
            return new ValidationResult(ValidationStatus.REJECTED, faithfulness, supported, total,
                    List.copyOf(citedValid), List.of("faithfulness below threshold: " + faithfulness));
        }
        return new ValidationResult(ValidationStatus.VALID, faithfulness, supported, total,
                List.copyOf(citedValid), List.of());
    }

    private Set<String> evidenceTargets(IncidentContext ctx) {
        Set<String> targets = new LinkedHashSet<>();
        for (IncidentContext.EventSummary e : ctx.events()) {
            if (e.sourceIp() != null) {
                targets.add(e.sourceIp());
            }
            if (e.username() != null) {
                targets.add(e.username());
            }
            if (e.host() != null) {
                targets.add(e.host());
            }
        }
        if (ctx.entity() != null && ctx.entity().value() != null) {
            targets.add(ctx.entity().value());
        }
        return targets;
    }

    private ValidationResult reject(List<String> reasons) {
        return new ValidationResult(ValidationStatus.REJECTED, 0.0, 0, 0, List.of(), reasons);
    }
}
