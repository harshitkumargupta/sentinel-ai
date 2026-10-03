package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.engine.AlertDraft;
import com.sentinelai.detection.engine.ConfigReader;
import com.sentinelai.detection.engine.DetectionRuleEvaluator;
import com.sentinelai.detection.engine.GroupBy;
import com.sentinelai.detection.engine.RuleContext;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Credential stuffing: many distinct usernames failing login from a single source IP within the
 * window. config: {@code {distinctUsers, windowSeconds}}.
 */
@Component
@RequiredArgsConstructor
public class CredentialStuffingRule implements DetectionRuleEvaluator {

    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return RuleTypes.CREDENTIAL_STUFFING;
    }

    @Override
    public Optional<AlertDraft> evaluate(SecurityEvent event, DetectionRule rule, RuleContext ctx) {
        if (event.getEventType() != EventType.FAILED_LOGIN || event.getSourceIp() == null) {
            return Optional.empty();
        }
        ConfigReader cfg = ConfigReader.of(rule.getConfig(), objectMapper);
        int distinctThreshold = cfg.getInt("distinctUsers", 5);
        int window = cfg.getInt("windowSeconds", 300);

        Long orgId = event.getOrg().getId();
        String ip = event.getSourceIp();
        long distinct = ctx.countDistinctUsers(orgId, EventType.FAILED_LOGIN, ip, window);
        if (distinct < distinctThreshold) {
            return Optional.empty();
        }
        List<Long> matched =
                ctx.recentEventIds(orgId, EventType.FAILED_LOGIN, GroupBy.SOURCE_IP, ip, window, 100);
        String message = "Credential stuffing: %d distinct users failing login from %s within %ds"
                .formatted(distinct, ip, window);
        return Optional.of(new AlertDraft(
                rule.getSeverity(), rule.getMitreTechnique(), message, "ip:" + ip, matched));
    }
}
