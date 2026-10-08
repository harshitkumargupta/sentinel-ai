package com.sentinelai.demo;

import com.sentinelai.alert.domain.Alert;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.cache.IncidentsChangedEvent;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.incident.domain.IncidentAlert;
import com.sentinelai.incident.repository.IncidentAlertRepository;
import com.sentinelai.simulator.SimulatorProperties;
import com.sentinelai.simulator.SimulatorService;
import com.sentinelai.simulator.TimeAnchor;
import com.sentinelai.simulator.domain.SimulatorRun;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Thin Demo Center layer over the existing {@link SimulatorService}: runs one simulator scenario
 * re-timed to "now" (so it shows up live), seeds a 24h benign baseline, resets demo data, and summarizes what each run produced (alerts, rules,
 * incidents) from the real pipeline's output.
 */
@Service
@RequiredArgsConstructor
public class DemoService {

    private final SimulatorService simulatorService;
    private final SimulatorProperties simulatorProperties;
    private final DemoProperties demoProperties;
    private final AlertRepository alertRepository;
    private final IncidentAlertRepository incidentAlertRepository;
    private final DemoDataCleaner cleaner;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public record ScenarioView(String id, String label, String description, String expectedRule, boolean inChain) {
    }

    public record RunSummary(String runId, String scenarioId, String label, int eventsGenerated, int alertsCreated,
                             List<String> rulesFired, List<String> mitre, String highestSeverity,
                             List<Long> incidentIds) {
    }

    public List<ScenarioView> scenarios() {
        return DemoCatalog.ordered(simulatorService.availableScenarios()).stream()
                .filter(id -> !DemoCatalog.BASELINE.equals(id))
                .map(id -> {
                    DemoCatalog.Entry e = DemoCatalog.describe(id);
                    return new ScenarioView(id, e.label(), e.description(), e.expectedRule(),
                            DemoCatalog.CHAIN.contains(id));
                })
                .toList();
    }

    /** The chain stages the simulator can actually run, in order. */
    public List<ScenarioView> chain() {
        return DemoCatalog.CHAIN.stream()
                .filter(simulatorService.availableScenarios()::contains)
                .map(id -> {
                    DemoCatalog.Entry e = DemoCatalog.describe(id);
                    return new ScenarioView(id, e.label(), e.description(), e.expectedRule(), true);
                })
                .toList();
    }

    public RunSummary run(Long orgId, String scenarioId) {
        if (!simulatorService.availableScenarios().contains(scenarioId)) {
            throw new NotFoundException("Unknown scenario: " + scenarioId);
        }
        SimulatorRun run = simulatorService.run(orgId, List.of(scenarioId), clock.millis(),
                simulatorProperties.getDefaultIntensity(), TimeAnchor.NOW);
        return summarize(orgId, scenarioId, run);
    }

    /** Benign baseline spread across the last 24 hours (the simulator's "normal" scenario). */
    public RunSummary seed(Long orgId) {
        int intensity = Math.max(1, demoProperties.getBaselineEvents() / 5);
        SimulatorRun run = simulatorService.run(orgId, List.of(DemoCatalog.BASELINE), clock.millis(),
                intensity, TimeAnchor.LAST_24H);
        return summarize(orgId, DemoCatalog.BASELINE, run);
    }

    public DemoDataCleaner.ResetResult reset(Long orgId) {
        DemoDataCleaner.ResetResult result = cleaner.reset(orgId);
        events.publishEvent(new IncidentsChangedEvent(orgId)); // drop cached dashboard aggregates
        return result;
    }

    private RunSummary summarize(Long orgId, String scenarioId, SimulatorRun run) {
        List<Alert> alerts = alertRepository.findByRunId(run.getRunId()).stream()
                .filter(a -> a.getOrg().getId().equals(orgId)).toList();
        List<Long> incidentIds = alerts.stream()
                .flatMap(a -> incidentAlertRepository.findById_AlertId(a.getId()).stream())
                .map(IncidentAlert::getIncident).map(i -> i.getId()).distinct().sorted().toList();
        String highest = alerts.stream().map(Alert::getSeverity).filter(Objects::nonNull)
                .max(Comparator.comparingInt(Severity::ordinal)).map(Enum::name).orElse(null);
        return new RunSummary(run.getRunId(), scenarioId, DemoCatalog.describe(scenarioId).label(),
                run.getEventsGenerated(), alerts.size(),
                alerts.stream().map(Alert::getRuleType).distinct().sorted().toList(),
                alerts.stream().map(Alert::getMitreTechnique).filter(Objects::nonNull).distinct().sorted().toList(),
                highest, incidentIds);
    }
}
