package com.sentinelai.detection.engine;

import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.event.domain.SecurityEvent;

import java.util.Optional;

/**
 * A pluggable detection rule. Register a new rule type by adding a Spring bean implementing this
 * interface — the engine discovers it by {@link #type()} (matched against
 * {@code detection_rules.rule_type}) and respects the rule's {@code enabled} flag. All thresholds,
 * windows, severity and MITRE id come from the rule's data-driven {@code config} JSON, so admins
 * edit behavior without a redeploy.
 */
public interface DetectionRuleEvaluator {

    String type();

    Optional<AlertDraft> evaluate(SecurityEvent event, DetectionRule rule, RuleContext ctx);
}
