package com.sentinelai.detection.backtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.detection.domain.BacktestRun;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.engine.AlertDraft;
import com.sentinelai.detection.engine.BacktestRuleContext;
import com.sentinelai.detection.engine.DetectionRuleEvaluator;
import com.sentinelai.detection.repository.BacktestRunRepository;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Replays stored events through a single rule with a (possibly overridden) config, in dry-run mode
 * (no alerts persisted), and records a {@code backtest_runs} row. Lets admins tune a rule safely.
 */
@Service
@RequiredArgsConstructor
public class BacktestService {

    private final DetectionRuleRepository ruleRepository;
    private final SecurityEventRepository eventRepository;
    private final BacktestRunRepository backtestRunRepository;
    private final List<DetectionRuleEvaluator> evaluatorBeans;

    @Transactional
    public BacktestResult backtest(Long ruleId, Long orgId, JsonNode configOverride, Instant from, Instant to) {
        DetectionRule rule = ruleRepository.findById(ruleId)
                .filter(r -> r.getOrg().getId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Rule not found: " + ruleId));

        Map<String, DetectionRuleEvaluator> evaluators = evaluatorBeans.stream()
                .collect(Collectors.toMap(DetectionRuleEvaluator::type, Function.identity()));
        DetectionRuleEvaluator evaluator = evaluators.get(rule.getRuleType());
        if (evaluator == null) {
            throw new BadRequestException("No evaluator for rule type " + rule.getRuleType());
        }

        String effectiveConfig = configOverride != null ? configOverride.toString() : rule.getConfig();
        DetectionRule probe = DetectionRule.builder()
                .id(rule.getId())
                .org(rule.getOrg())
                .name(rule.getName())
                .ruleType(rule.getRuleType())
                .severity(rule.getSeverity())
                .mitreTechnique(rule.getMitreTechnique())
                .version(rule.getVersion())
                .config(effectiveConfig)
                .enabled(true)
                .build();

        List<SecurityEvent> events = eventRepository
                .findByOrg_IdAndEventTimestampBetweenOrderByEventTimestampAsc(orgId, from, to);

        BacktestRuleContext ctx = new BacktestRuleContext();
        long alertsFired = 0;
        List<String> samples = new ArrayList<>();
        for (SecurityEvent event : events) {
            ctx.advance(event);
            Optional<AlertDraft> draft = evaluator.evaluate(event, probe, ctx);
            if (draft.isPresent()) {
                alertsFired++;
                if (samples.size() < 10) {
                    samples.add(draft.get().message());
                }
            }
        }

        backtestRunRepository.save(BacktestRun.builder()
                .rule(rule)
                .eventsScanned((int) events.size())
                .alertsFired((int) alertsFired)
                .configSnapshot(effectiveConfig)
                .build());

        return new BacktestResult(events.size(), alertsFired, samples);
    }
}
