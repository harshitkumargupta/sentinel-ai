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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Runs simulator scenarios: generates deterministic labeled events, ingests them through the real
 * pipeline (so the detection engine produces alerts), and records ground-truth labels for scoring.
 */
@Slf4j
@Service
public class SimulatorService {

    private static final long LAST_24H_SECONDS = 24 * 3600L - 60;

    private final Map<String, Scenario> scenarios;
    private final IngestionService ingestionService;
    private final SimLabelRepository simLabelRepository;
    private final SimulatorRunRepository simulatorRunRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SimulatorService(List<Scenario> scenarioBeans,
                            IngestionService ingestionService,
                            SimLabelRepository simLabelRepository,
                            SimulatorRunRepository simulatorRunRepository,
                            ObjectMapper objectMapper,
                            Clock clock) {
        this.scenarios = scenarioBeans.stream()
                .collect(Collectors.toMap(Scenario::id, Function.identity()));
        this.ingestionService = ingestionService;
        this.simLabelRepository = simLabelRepository;
        this.simulatorRunRepository = simulatorRunRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Set<String> availableScenarios() {
        return scenarios.keySet();
    }

    public SimulatorRun run(Long orgId, List<String> requested, long seed, int intensity) {
        return run(orgId, requested, seed, intensity, TimeAnchor.FIXED);
    }

    public SimulatorRun run(Long orgId, List<String> requested, long seed, int intensity, TimeAnchor anchor) {
        List<Scenario> toRun = resolve(requested);
        String runId = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        SimContext ctx = new SimContext(seed, intensity);

        List<Generated> batch = new ArrayList<>();
        for (Scenario scenario : toRun) {
            int idx = 0;
            for (GeneratedEvent ge : scenario.generate(ctx)) {
                batch.add(new Generated(scenario.id(), idx++, ge));
            }
        }
        UnaryOperator<Instant> retime = retimer(anchor == null ? TimeAnchor.FIXED : anchor, batch);

        int generated = 0;
        RunContextHolder.set(runId);
        try {
            for (Generated g : batch) {
                String clientEventId = runId + ":" + g.scenarioId() + ":" + g.index();
                Map<String, Object> payload = retimed(g.event().payload(), retime);
                IngestOutcome outcome = ingestionService.ingest(
                        orgId, null, "generic", objectMapper.valueToTree(payload), clientEventId);
                simLabelRepository.save(SimLabel.builder()
                        .eventId(outcome.eventId())
                        .scenarioId(g.event().scenarioId())
                        .runId(runId)
                        .attack(g.event().attack())
                        .expectedRule(g.event().expectedRule())
                        .build());
                generated++;
            }
        } finally {
            RunContextHolder.clear();
        }

        String scenarioList = toRun.stream().map(Scenario::id).collect(Collectors.joining(","));
        log.info("Simulator run {} generated {} events across [{}] (time anchor {})",
                runId, generated, scenarioList, anchor);
        return simulatorRunRepository.save(SimulatorRun.builder()
                .runId(runId)
                .seed(seed)
                .scenarios(scenarioList)
                .intensity(intensity)
                .eventsGenerated(generated)
                .build());
    }

    private record Generated(String scenarioId, int index, GeneratedEvent event) {
    }

    private UnaryOperator<Instant> retimer(TimeAnchor anchor, List<Generated> batch) {
        List<Instant> times = batch.stream().map(g -> timestamp(g.event().payload()))
                .filter(java.util.Objects::nonNull).toList();
        if (anchor == TimeAnchor.FIXED || times.isEmpty()) {
            return UnaryOperator.identity();
        }
        Instant min = times.stream().min(Instant::compareTo).orElseThrow();
        Instant max = times.stream().max(Instant::compareTo).orElseThrow();
        Instant now = clock.instant();
        if (anchor == TimeAnchor.NOW) {
            Duration shift = Duration.between(max, now);
            return t -> t.plus(shift);
        }
        long span = Math.max(1, Duration.between(min, max).getSeconds());
        long target = LAST_24H_SECONDS;
        return t -> now.minusSeconds(target - Duration.between(min, t).getSeconds() * target / span);
    }

    private static Map<String, Object> retimed(Map<String, Object> payload, UnaryOperator<Instant> retime) {
        Instant ts = timestamp(payload);
        if (ts == null) {
            return payload;
        }
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        copy.put("eventTimestamp", retime.apply(ts).toString());
        return copy;
    }

    private static Instant timestamp(Map<String, Object> payload) {
        Object raw = payload.get("eventTimestamp");
        try {
            return raw == null ? null : Instant.parse(raw.toString());
        } catch (java.time.format.DateTimeParseException e) {
            return null; // the normalizer falls back to ingest time for unparsable timestamps
        }
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
