package com.sentinelai.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.engine.RunContextHolder;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.ingestion.IngestionService.IngestOutcome;
import com.sentinelai.simulator.domain.SimLabel;
import com.sentinelai.simulator.domain.SimulatorRun;
import com.sentinelai.simulator.repository.SimLabelRepository;
import com.sentinelai.simulator.repository.SimulatorRunRepository;
import com.sentinelai.simulator.scenario.GeneratedEvent;
import com.sentinelai.simulator.scenario.Scenario;
import com.sentinelai.simulator.scenario.SimContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs simulator scenarios: generates deterministic labeled events, ingests them through the real
 * pipeline (so the detection engine produces alerts), and records ground-truth labels for scoring.
 */
@Slf4j
@Service
public class SimulatorService {

    private final Map<String, Scenario> scenarios;
    private final IngestionService ingestionService;
    private final SimLabelRepository simLabelRepository;
    private final SimulatorRunRepository simulatorRunRepository;
    private final ObjectMapper objectMapper;

    public SimulatorService(List<Scenario> scenarioBeans,
                            IngestionService ingestionService,
                            SimLabelRepository simLabelRepository,
                            SimulatorRunRepository simulatorRunRepository,
                            ObjectMapper objectMapper) {
        this.scenarios = scenarioBeans.stream()
                .collect(Collectors.toMap(Scenario::id, Function.identity()));
        this.ingestionService = ingestionService;
        this.simLabelRepository = simLabelRepository;
        this.simulatorRunRepository = simulatorRunRepository;
        this.objectMapper = objectMapper;
    }

    public Set<String> availableScenarios() {
        return scenarios.keySet();
    }

    public SimulatorRun run(Long orgId, List<String> requested, long seed, int intensity) {
        List<Scenario> toRun = resolve(requested);
        String runId = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        SimContext ctx = new SimContext(seed, intensity);
        int generated = 0;

        RunContextHolder.set(runId);
        try {
            for (Scenario scenario : toRun) {
                List<GeneratedEvent> events = scenario.generate(ctx);
                int idx = 0;
                for (GeneratedEvent ge : events) {
                    String clientEventId = runId + ":" + scenario.id() + ":" + idx++;
                    IngestOutcome outcome = ingestionService.ingest(
                            orgId, "generic", objectMapper.valueToTree(ge.payload()), clientEventId);
                    simLabelRepository.save(SimLabel.builder()
                            .eventId(outcome.eventId())
                            .scenarioId(ge.scenarioId())
                            .runId(runId)
                            .attack(ge.attack())
                            .expectedRule(ge.expectedRule())
                            .build());
                    generated++;
                }
            }
        } finally {
            RunContextHolder.clear();
        }

        String scenarioList = toRun.stream().map(Scenario::id).collect(Collectors.joining(","));
        log.info("Simulator run {} generated {} events across [{}]", runId, generated, scenarioList);
        return simulatorRunRepository.save(SimulatorRun.builder()
                .runId(runId)
                .seed(seed)
                .scenarios(scenarioList)
                .intensity(intensity)
                .eventsGenerated(generated)
                .build());
    }

    public List<SimulatorRun> recentRuns() {
        return simulatorRunRepository.findTop50ByOrderByIdDesc();
    }

    private List<Scenario> resolve(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return scenarios.values().stream().toList();
        }
        return requested.stream().map(scenarios::get).filter(java.util.Objects::nonNull).toList();
    }
}
