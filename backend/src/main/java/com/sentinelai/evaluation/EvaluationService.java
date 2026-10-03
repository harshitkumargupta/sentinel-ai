package com.sentinelai.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.alert.domain.Alert;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.evaluation.dto.EvaluationResult;
import com.sentinelai.evaluation.dto.EvaluationResult.AlertReduction;
import com.sentinelai.evaluation.dto.EvaluationResult.IncidentLevel;
import com.sentinelai.evaluation.dto.Metrics;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.incident.domain.IncidentAlert;
import com.sentinelai.incident.domain.IncidentEvent;
import com.sentinelai.incident.repository.IncidentAlertRepository;
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.simulator.domain.SimLabel;
import com.sentinelai.simulator.repository.SimLabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Scores detection against simulator ground truth: overall and per-rule precision/recall/F1 at the
 * event level, plus mean detection latency (event-time span from the earliest contributing event to
 * the triggering event).
 */
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final SimLabelRepository simLabelRepository;
    private final AlertRepository alertRepository;
    private final SecurityEventRepository eventRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final IncidentAlertRepository incidentAlertRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public EvaluationResult evaluate(String runId) {
        List<SimLabel> labels = simLabelRepository.findByRunId(runId);
        List<Alert> alerts = alertRepository.findByRunId(runId);

        // eventId -> set of rule types that alerted on it (via triggering or matched events)
        Map<Long, Set<String>> detectedBy = new HashMap<>();
        for (Alert alert : alerts) {
            for (Long eventId : referencedEvents(alert)) {
                detectedBy.computeIfAbsent(eventId, k -> new HashSet<>()).add(alert.getRuleType());
            }
        }
        Set<Long> detected = detectedBy.keySet();

        // ---- overall (event level) ----
        long tp = 0;
        long fp = 0;
        long fn = 0;
        for (SimLabel label : labels) {
            boolean predicted = detected.contains(label.getEventId());
            if (label.isAttack() && predicted) {
                tp++;
            } else if (!label.isAttack() && predicted) {
                fp++;
            } else if (label.isAttack()) {
                fn++;
            }
        }

        // ---- per rule ----
        Set<String> ruleTypes = new TreeSet<>();
        labels.stream().map(SimLabel::getExpectedRule).filter(r -> r != null).forEach(ruleTypes::add);
        alerts.stream().map(Alert::getRuleType).forEach(ruleTypes::add);

        Map<String, Metrics> perRule = new LinkedHashMap<>();
        for (String rule : ruleTypes) {
            Set<Long> expected = new HashSet<>();
            for (SimLabel label : labels) {
                if (label.isAttack() && rule.equals(label.getExpectedRule())) {
                    expected.add(label.getEventId());
                }
            }
            Set<Long> fired = new HashSet<>();
            detectedBy.forEach((eventId, rules) -> {
                if (rules.contains(rule)) {
                    fired.add(eventId);
                }
            });
            long rtp = expected.stream().filter(fired::contains).count();
            long rfn = expected.size() - rtp;
            long rfp = fired.stream().filter(id -> !expected.contains(id)).count();
            perRule.put(rule, Metrics.of(rtp, rfp, rfn));
        }

        // ---- incident level: did each attack scenario produce (ideally one) incident? ----
        Map<String, Set<Long>> attackEventsByScenario = new LinkedHashMap<>();
        for (SimLabel label : labels) {
            if (label.isAttack()) {
                attackEventsByScenario.computeIfAbsent(label.getScenarioId(), k -> new HashSet<>())
                        .add(label.getEventId());
            }
        }
        int detectedScenarios = 0;
        int exactlyOne = 0;
        Set<Long> attackIncidentIds = new HashSet<>();
        for (Set<Long> eventIds : attackEventsByScenario.values()) {
            Set<Long> incidentIds = new HashSet<>();
            for (Long eventId : eventIds) {
                for (IncidentEvent ie : incidentEventRepository.findById_EventId(eventId)) {
                    incidentIds.add(ie.getId().getIncidentId());
                }
            }
            if (!incidentIds.isEmpty()) {
                detectedScenarios++;
            }
            if (incidentIds.size() == 1) {
                exactlyOne++;
            }
            attackIncidentIds.addAll(incidentIds);
        }
        Set<Long> runIncidentIds = new HashSet<>();
        for (Alert alert : alerts) {
            for (IncidentAlert ia : incidentAlertRepository.findById_AlertId(alert.getId())) {
                runIncidentIds.add(ia.getId().getIncidentId());
            }
        }
        int attackScenarios = attackEventsByScenario.size();
        long legitIncidents = runIncidentIds.stream().filter(attackIncidentIds::contains).count();
        double incRecall = attackScenarios == 0 ? 0 : round3((double) detectedScenarios / attackScenarios);
        double incPrecision = runIncidentIds.isEmpty() ? 0
                : round3((double) legitIncidents / runIncidentIds.size());
        IncidentLevel incidentLevel = new IncidentLevel(
                incPrecision, incRecall, attackScenarios, detectedScenarios, exactlyOne);

        long eventCount = labels.size();
        long incidentCount = runIncidentIds.size();
        double reductionPct = eventCount == 0 ? 0
                : Math.round((1.0 - (double) incidentCount / eventCount) * 10000.0) / 100.0;
        AlertReduction alertReduction = new AlertReduction(eventCount, alerts.size(), incidentCount, reductionPct);

        return new EvaluationResult(runId, labels.size(), alerts.size(),
                Metrics.of(tp, fp, fn), perRule, meanLatencySeconds(alerts),
                incidentLevel, alertReduction);
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private double meanLatencySeconds(List<Alert> alerts) {
        if (alerts.isEmpty()) {
            return 0.0;
        }
        // Gather all referenced event timestamps in one query.
        Set<Long> ids = new HashSet<>();
        alerts.forEach(a -> ids.addAll(referencedEvents(a)));
        Map<Long, Instant> times = new HashMap<>();
        eventRepository.findAllById(ids).forEach(e -> times.put(e.getId(), e.getEventTimestamp()));

        double total = 0;
        int counted = 0;
        for (Alert alert : alerts) {
            List<Long> refs = referencedEvents(alert);
            Instant trigger = times.get(alert.getTriggeringEventId());
            Instant earliest = refs.stream().map(times::get).filter(t -> t != null)
                    .min(Instant::compareTo).orElse(null);
            if (trigger != null && earliest != null) {
                total += Math.max(0, Duration.between(earliest, trigger).toMillis() / 1000.0);
                counted++;
            }
        }
        return counted == 0 ? 0.0 : Math.round(total / counted * 100.0) / 100.0;
    }

    private List<Long> referencedEvents(Alert alert) {
        List<Long> ids = new ArrayList<>();
        if (alert.getTriggeringEventId() != null) {
            ids.add(alert.getTriggeringEventId());
        }
        if (alert.getMatchedEventIds() != null) {
            try {
                for (Long id : objectMapper.readValue(alert.getMatchedEventIds(), Long[].class)) {
                    ids.add(id);
                }
            } catch (Exception ignored) {
                // tolerate malformed json
            }
        }
        return ids;
    }
}
