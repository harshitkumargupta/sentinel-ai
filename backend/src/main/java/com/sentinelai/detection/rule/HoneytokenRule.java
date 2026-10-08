package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.util.Hashing;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.engine.AlertDraft;
import com.sentinelai.detection.engine.DetectionRuleEvaluator;
import com.sentinelai.detection.engine.RuleContext;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.honeytoken.domain.Honeytoken;
import com.sentinelai.honeytoken.repository.HoneytokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Honeytoken access: hashes a candidate value from the event and checks it against the
 * {@code honeytokens} table. Any hit is a high-fidelity breach signal — always CRITICAL — and bumps
 * the honeytoken's {@code triggered_count}.
 */
@Component
@RequiredArgsConstructor
public class HoneytokenRule implements DetectionRuleEvaluator {

    private final HoneytokenRepository honeytokenRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return RuleTypes.HONEYTOKEN;
    }

    @Override
    public Optional<AlertDraft> evaluate(SecurityEvent event, DetectionRule rule, RuleContext ctx) {
        Long orgId = event.getOrg().getId();
        for (String candidate : candidates(event)) {
            Optional<Honeytoken> match = honeytokenRepository.findFirstByOrg_IdAndValueHash(orgId, Hashing.sha256Hex(candidate));
            if (match.isEmpty()) {
                continue;
            }
            Honeytoken token = match.get();
            if (!ctx.dryRun()) { // don't mutate counters during a backtest
                token.setTriggeredCount(token.getTriggeredCount() + 1);
                token.setLastTriggeredAt(event.getEventTimestamp());
                honeytokenRepository.save(token);
            }
            String label = token.getDisplayValue() != null ? token.getDisplayValue() : token.getType();
            String message = ("Decoy touched: %s %s (%s). Nothing legitimate uses this value, so any use means "
                    + "someone is probing or holds stolen data.").formatted(token.getKind(), label,
                    token.getDescription() == null ? token.getType() : token.getDescription());
            // A honeytoken hit is unambiguous — force CRITICAL regardless of the rule's configured severity.
            return Optional.of(new AlertDraft(
                    Severity.CRITICAL, rule.getMitreTechnique(), message,
                    "honeytoken:" + token.getId(), List.of(event.getId())));
        }
        return Optional.empty();
    }

    /** Every place a decoy can show up: explicit value / API key, the resource (with and without query), the username. */
    private List<String> candidates(SecurityEvent event) {
        List<String> out = new java.util.ArrayList<>();
        if (event.getRawPayload() != null && !event.getRawPayload().isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(event.getRawPayload());
                for (String f : List.of("value", "apiKey", "token")) {
                    if (node.hasNonNull(f)) {
                        out.add(node.get(f).asText());
                    }
                }
            } catch (Exception ignored) {
                // not JSON: the structured fields below still apply
            }
        }
        if (event.getResource() != null) {
            out.add(event.getResource());
            int q = event.getResource().indexOf('?');
            if (q > 0) {
                out.add(event.getResource().substring(0, q));
            }
        }
        if (event.getUsername() != null) {
            out.add(event.getUsername());
        }
        return out.stream().filter(v -> !v.isBlank()).distinct().toList();
    }
}
