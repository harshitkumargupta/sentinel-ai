package com.sentinelai.detection.tuning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.engine.AlertDraft;
import com.sentinelai.detection.engine.BacktestRuleContext;
import com.sentinelai.detection.engine.DetectionRuleEvaluator;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentFeedback;
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Detection-tuning from analyst feedback. Measures a rule's false-positive rate over reviewed
 * (TRUE_POSITIVE/FALSE_POSITIVE) incidents, then replays the rule at higher thresholds to report —
 * at the incident level — how many false positives a change would remove and how many true positives
 * it would lose. An incident "fires" at a config if the rule fires on any of its events. Advisory
 * only, never auto-applied, and only offered above a minimum reviewed sample size.
 */
@Slf4j
@Service
public class TuningService {

    private record Labeled(Long incidentId, IncidentFeedback feedback, Set<Long> eventIds) {
    }

    private final DetectionRuleRepository ruleRepository;
    private final SecurityEventRepository eventRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final java.util.Map<String, DetectionRuleEvaluator> evaluators;
    private final ObjectMapper objectMapper;
    private final TuningProperties props;

    public TuningService(DetectionRuleRepository ruleRepository, SecurityEventRepository eventRepository,
                         IncidentRepository incidentRepository, IncidentEventRepository incidentEventRepository,
                         List<DetectionRuleEvaluator> evaluatorBeans, ObjectMapper objectMapper,
                         TuningProperties props) {
        this.ruleRepository = ruleRepository;
        this.eventRepository = eventRepository;
        this.incidentRepository = incidentRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.evaluators = evaluatorBeans.stream()
                .collect(Collectors.toMap(DetectionRuleEvaluator::type, Function.identity()));
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public RuleTuning forRule(Long ruleId, Long orgId) {
        DetectionRule rule = ruleRepository.findById(ruleId)
                .filter(r -> r.getOrg().getId().equals(orgId))
                .orElseThrow(() -> new com.sentinelai.common.exception.NotFoundException("Rule not found: " + ruleId));
        return compute(rule, orgId, labeledIncidents(orgId));
    }

    @Transactional(readOnly = true)
    public List<RuleTuning> forOrg(Long orgId) {
        List<Labeled> labeled = labeledIncidents(orgId);
        return ruleRepository.findByOrg_IdAndEnabledTrue(orgId).stream()
                .map(r -> compute(r, orgId, labeled))
                .sorted(Comparator.comparingDouble(RuleTuning::fpRate).reversed())
                .toList();
    }

    private RuleTuning compute(DetectionRule rule, Long orgId, List<Labeled> labeled) {
        DetectionRuleEvaluator evaluator = evaluators.get(rule.getRuleType());
        if (evaluator == null || labeled.isEmpty()) {
            return new RuleTuning(rule.getId(), rule.getName(), rule.getRuleType(), 0, 0, 0, 0, false, null);
        }
        Set<Long> allEventIds = labeled.stream().flatMap(l -> l.eventIds().stream()).collect(Collectors.toSet());
        List<SecurityEvent> events = windowEvents(orgId, allEventIds);
        Set<Long> firedCurrent = replay(evaluator, rule, rule.getConfig(), events);

        List<Labeled> firing = labeled.stream().filter(l -> fires(l, firedCurrent)).toList();
        int firedFP = (int) firing.stream().filter(l -> l.feedback() == IncidentFeedback.FALSE_POSITIVE).count();
        int firedTP = (int) firing.stream().filter(l -> l.feedback() == IncidentFeedback.TRUE_POSITIVE).count();
        int sampleSize = firedFP + firedTP;
        double fpRate = sampleSize == 0 ? 0.0 : round((double) firedFP / sampleSize);
        boolean enough = sampleSize >= props.getMinSamples();

        RuleTuning.ThresholdSuggestion suggestion = null;
        Integer threshold = parseThreshold(rule.getConfig());
        if (enough && firedFP > 0 && threshold != null) {
            suggestion = bestThreshold(evaluator, rule, events, firing, threshold, firedFP);
        }
        return new RuleTuning(rule.getId(), rule.getName(), rule.getRuleType(),
                firedFP, firedTP, fpRate, sampleSize, enough, suggestion);
    }

    private RuleTuning.ThresholdSuggestion bestThreshold(DetectionRuleEvaluator evaluator, DetectionRule rule,
                                                         List<SecurityEvent> events, List<Labeled> firing,
                                                         int threshold, int firedFP) {
        RuleTuning.ThresholdSuggestion best = null;
        double bestScore = 0;
        for (int to = threshold + 1; to <= threshold + props.getMaxThresholdDelta(); to++) {
            String config = withThreshold(rule.getConfig(), to);
            Set<Long> firedCandidate = replay(evaluator, rule, config, events);
            int removedFP = 0;
            int lostTP = 0;
            for (Labeled l : firing) {
                if (!fires(l, firedCandidate)) { // fired at current, no longer fires
                    if (l.feedback() == IncidentFeedback.FALSE_POSITIVE) {
                        removedFP++;
                    } else if (l.feedback() == IncidentFeedback.TRUE_POSITIVE) {
                        lostTP++;
                    }
                }
            }
            if (removedFP == 0) {
                continue;
            }
            double score = removedFP - 1000.0 * lostTP; // strongly prefer not losing true positives
            if (best == null || score > bestScore) {
                bestScore = score;
                double pct = round((double) removedFP / firedFP);
                String summary = ("raise threshold %d -> %d: removes %.0f%% of false positives (%d), "
                        + "loses %d true positive(s)").formatted(threshold, to, pct * 100, removedFP, lostTP);
                best = new RuleTuning.ThresholdSuggestion("threshold", threshold, to, removedFP, pct, lostTP,
                        config, summary);
            }
        }
        return best;
    }

    private boolean fires(Labeled incident, Set<Long> firedEventIds) {
        return !Collections.disjoint(incident.eventIds(), firedEventIds);
    }

    private Set<Long> replay(DetectionRuleEvaluator evaluator, DetectionRule rule, String config,
                             List<SecurityEvent> events) {
        DetectionRule probe = DetectionRule.builder()
                .id(rule.getId()).org(rule.getOrg()).name(rule.getName()).ruleType(rule.getRuleType())
                .severity(rule.getSeverity()).mitreTechnique(rule.getMitreTechnique())
                .version(rule.getVersion()).config(config).enabled(true).build();
        BacktestRuleContext ctx = new BacktestRuleContext();
        Set<Long> fired = new HashSet<>();
        for (SecurityEvent e : events) {
            ctx.advance(e);
            Optional<AlertDraft> draft = evaluator.evaluate(e, probe, ctx);
            if (draft.isPresent()) {
                fired.add(e.getId());
            }
        }
        return fired;
    }

    private List<Labeled> labeledIncidents(Long orgId) {
        List<Labeled> out = new ArrayList<>();
        for (Incident inc : incidentRepository.findByOrg_IdAndFeedbackIn(orgId,
                List.of(IncidentFeedback.TRUE_POSITIVE, IncidentFeedback.FALSE_POSITIVE))) {
            Set<Long> eventIds = incidentEventRepository.findById_IncidentId(inc.getId()).stream()
                    .map(link -> link.getEvent().getId()).collect(Collectors.toSet());
            if (!eventIds.isEmpty()) {
                out.add(new Labeled(inc.getId(), inc.getFeedback(), eventIds));
            }
        }
        return out;
    }

    private List<SecurityEvent> windowEvents(Long orgId, Set<Long> labeledIds) {
        List<SecurityEvent> labeled = eventRepository.findAllById(labeledIds);
        Instant min = labeled.stream().map(SecurityEvent::getEventTimestamp).min(Instant::compareTo)
                .orElse(Instant.now()).minus(1, ChronoUnit.DAYS);
        Instant max = labeled.stream().map(SecurityEvent::getEventTimestamp).max(Instant::compareTo)
                .orElse(Instant.now()).plus(1, ChronoUnit.DAYS);
        return eventRepository.findByOrg_IdAndEventTimestampBetweenOrderByEventTimestampAsc(orgId, min, max);
    }

    private Integer parseThreshold(String config) {
        try {
            var node = objectMapper.readTree(config).path("threshold");
            return node.isInt() ? node.asInt() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String withThreshold(String config, int value) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(config);
            node.put("threshold", value);
            return node.toString();
        } catch (Exception e) {
            return config;
        }
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
