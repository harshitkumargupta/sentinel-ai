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
        String candidate = candidateValue(event);
        if (candidate == null || candidate.isBlank()) {
            return Optional.empty();
        }
        Optional<Honeytoken> match = honeytokenRepository.findByValueHash(Hashing.sha256Hex(candidate));
        if (match.isEmpty()) {
            return Optional.empty();
        }
        Honeytoken token = match.get();
        if (!ctx.dryRun()) { // don't mutate counters during a backtest
            token.setTriggeredCount(token.getTriggeredCount() + 1);
            honeytokenRepository.save(token);
        }

        String message = "Honeytoken touched: %s (%s)".formatted(token.getType(), token.getDescription());
        // A honeytoken hit is unambiguous — force CRITICAL regardless of the rule's configured severity.
        return Optional.of(new AlertDraft(
                Severity.CRITICAL, rule.getMitreTechnique(), message,
                "honeytoken:" + token.getId(), List.of(event.getId())));
    }

    private String candidateValue(SecurityEvent event) {
        if (event.getRawPayload() != null && !event.getRawPayload().isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(event.getRawPayload()).get("value");
                if (node != null && !node.isNull()) {
                    return node.asText();
                }
            } catch (Exception ignored) {
                // fall through to other candidates
            }
        }
        if (event.getResource() != null) {
            return event.getResource();
        }
        return event.getUsername();
    }
}
