package com.sentinelai.detection.engine;

import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.event.domain.SecurityEvent;

import java.util.Optional;

/**
 * A pluggable detection algorithm. Register a new detection type by adding a Spring bean that
 * implements this interface — the engine discovers it by {@link #type()}, with no engine edits.
 * Rule parameters come from the rule's data-driven {@code config} JSON.
 */
public interface DetectionStrategy {

    /** The {@code detection_rules.rule_type} value this strategy handles (e.g. "THRESHOLD"). */
    String type();

    /**
     * Evaluate a rule against the triggering event.
     *
     * @return an outcome if the rule fires, otherwise {@link Optional#empty()}
     */
    Optional<DetectionOutcome> evaluate(SecurityEvent event, DetectionRule rule, DetectionContext ctx);
}
