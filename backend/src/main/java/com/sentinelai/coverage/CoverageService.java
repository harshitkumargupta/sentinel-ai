package com.sentinelai.coverage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.alert.domain.Alert;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.audit.service.AuditService;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.common.mitre.MitreCatalog;
import com.sentinelai.coverage.CoverageReport.CellStatus;
import com.sentinelai.coverage.CoverageReport.Gap;
import com.sentinelai.coverage.CoverageReport.MatrixCell;
import com.sentinelai.coverage.CoverageReport.ScenarioResult;
import com.sentinelai.demo.DemoCatalog;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.report.ReportTable;
import com.sentinelai.simulator.SimulatorProperties;
import com.sentinelai.simulator.SimulatorService;
import com.sentinelai.simulator.TimeAnchor;
import com.sentinelai.simulator.domain.SimulatorRun;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Detection coverage: runs each existing simulator attack scenario (re-timed to now, tagged like any
 * simulator run so Reset Demo Data removes it), checks whether its expected rule fired, measures time
 * to detect, maps results onto the offline MITRE catalog (detected / missed / untested), and lists gaps
 * with suggestions. Coverage % = detected ÷ tested scenarios. Runs are stored to show improvement.
 */
@Service
@RequiredArgsConstructor
public class CoverageService {

    private final SimulatorService simulator;
    private final SimulatorProperties simulatorProperties;
    private final AlertRepository alerts;
    private final DetectionRuleRepository rules;
    private final MitreCatalog mitre;
    private final CoverageRunRepository runs;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public record RunView(Long id, Instant createdAt, double coveragePct, int detected, int tested, CoverageReport report) {
    }

    public RunView run(Long orgId, Long userId) {
        if (!simulatorProperties.isEnabled()) {
            throw new BadRequestException("Coverage runs need the simulator (demo/dev profile)");
        }
        Map<String, DetectionRule> rulesByType = rules.findByOrg_IdOrderByIdAsc(orgId).stream()
                .collect(Collectors.toMap(DetectionRule::getRuleType, r -> r, (a, b) -> a.isEnabled() ? a : b, LinkedHashMap::new));
        List<ScenarioResult> results = new ArrayList<>();
        for (String id : DemoCatalog.ordered(simulator.availableScenarios())) {
            DemoCatalog.Entry e = DemoCatalog.describe(id);
            if (e.expectedRule() == null || DemoCatalog.TIME_DEPENDENT.contains(id)) {
                continue; // benign, or can't be exercised when re-timed to now
            }
            Instant start = clock.instant();
            SimulatorRun run = simulator.run(orgId, List.of(id), clock.millis(), simulatorProperties.getDefaultIntensity(), TimeAnchor.NOW);
            List<Alert> fired = alerts.findByRunId(run.getRunId()).stream().filter(a -> a.getOrg().getId().equals(orgId)).toList();
            boolean detected = fired.stream().anyMatch(a -> a.getRuleType().equals(e.expectedRule()));
            Long ttd = fired.stream().map(Alert::getCreatedAt).filter(Objects::nonNull).min(Comparator.naturalOrder())
                    .map(t -> Math.max(0, Duration.between(start, t).toMillis())).orElse(null);
            DetectionRule rule = rulesByType.get(e.expectedRule());
            String technique = rule != null ? rule.getMitreTechnique() : null;
            results.add(new ScenarioResult(id, e.label(), e.expectedRule(), technique, detected,
                    fired.stream().map(Alert::getRuleType).distinct().sorted().toList(), fired.size(),
                    detected ? ttd : null, run.getEventsGenerated()));
        }
        CoverageReport report = report(results, rulesByType);
        CoverageRun saved = runs.save(CoverageRun.builder().orgId(orgId).createdBy(userId)
                .coveragePct(BigDecimal.valueOf(report.coveragePct()).setScale(1, RoundingMode.HALF_UP))
                .detected(report.detected()).tested(report.tested()).report(write(report)).build());
        audit.record(orgId, userId, "COVERAGE_RUN", "coverage_run", saved.getId(),
                "{\"coverage\":" + report.coveragePct() + "}", null);
        return view(saved);
    }

    /** Pure scoring step (unit-tested): score, matrix and gaps from scenario results + current rules. */
    CoverageReport report(List<ScenarioResult> results, Map<String, DetectionRule> rulesByType) {
        int tested = results.size();
        int detected = (int) results.stream().filter(ScenarioResult::detected).count();
        double pct = tested == 0 ? 0 : Math.round(1000.0 * detected / tested) / 10.0;

        Map<String, CellStatus> status = new LinkedHashMap<>();
        Map<String, String> detail = new LinkedHashMap<>();
        for (ScenarioResult r : results) {
            if (r.technique() == null) {
                continue;
            }
            CellStatus s = r.detected() ? CellStatus.DETECTED : CellStatus.MISSED;
            if (status.get(r.technique()) != CellStatus.DETECTED) {
                status.put(r.technique(), s);
            }
            detail.merge(r.technique(), r.label() + (r.detected() ? " ✓" : " ✗"), (a, b) -> a + ", " + b);
        }
        List<MatrixCell> matrix = mitre.all().stream().map(t -> new MatrixCell(t.id(), t.name(), t.tactic(),
                status.getOrDefault(t.id(), CellStatus.UNTESTED), detail.get(t.id()))).toList();

        List<Gap> gaps = new ArrayList<>();
        for (ScenarioResult r : results) {
            if (r.detected()) {
                continue;
            }
            DetectionRule rule = rulesByType.get(r.expectedRule());
            if (rule == null) {
                gaps.add(new Gap(r.label(), "No " + r.expectedRule() + " rule exists", "Create a " + r.expectedRule() + " rule on the Rules page"));
            } else if (!rule.isEnabled()) {
                gaps.add(new Gap(r.label(), "Rule '" + rule.getName() + "' is disabled", "Enable it on the Rules page"));
            } else {
                gaps.add(new Gap(r.label(), "Rule '" + rule.getName() + "' did not fire", "Lower its threshold / widen its window, then backtest"));
            }
        }
        for (MatrixCell c : matrix) {
            if (c.status() != CellStatus.UNTESTED) {
                continue;
            }
            boolean ruleExists = rulesByType.values().stream().anyMatch(r -> c.technique().equals(r.getMitreTechnique()) && r.isEnabled());
            gaps.add(new Gap(c.technique() + " " + c.name(), ruleExists ? "A rule maps here but no scenario tests it"
                    : "No enabled rule maps to this technique",
                    ruleExists ? "Replay a sample dataset or upload logs that exercise it" : "Add a rule (or building block) for " + c.technique()));
        }
        return new CoverageReport(pct, detected, tested, results, matrix, gaps);
    }

    public List<RunView> history(Long orgId) {
        return runs.findByOrgIdOrderByIdDesc(orgId, PageRequest.of(0, 30)).stream().map(this::view).toList();
    }

    public RunView get(Long orgId, Long id) {
        return runs.findById(id).filter(r -> r.getOrgId().equals(orgId)).map(this::view)
                .orElseThrow(() -> new NotFoundException("Coverage run not found: " + id));
    }

    /** The run as a report table (for the shared PDF/CSV renderers). */
    public ReportTable table(RunView r) {
        List<String> summary = new ArrayList<>(List.of(
                "Coverage: " + r.coveragePct() + "% (" + r.detected() + " of " + r.tested() + " scenarios detected)",
                "MITRE techniques: " + r.report().matrix().stream().filter(c -> c.status() == CellStatus.DETECTED).count()
                        + " detected, " + r.report().matrix().stream().filter(c -> c.status() == CellStatus.MISSED).count()
                        + " missed, " + r.report().matrix().stream().filter(c -> c.status() == CellStatus.UNTESTED).count() + " untested"));
        r.report().gaps().stream().limit(10).forEach(g -> summary.add("Gap: " + g.item() + " - " + g.reason() + ". " + g.suggestion()));
        List<List<String>> rows = r.report().scenarios().stream().map(s -> List.of(s.label(), s.expectedRule(),
                s.technique() == null ? "" : s.technique(), s.detected() ? "DETECTED" : "MISSED", String.join(" ", s.rulesFired()),
                String.valueOf(s.alerts()), s.timeToDetectMs() == null ? "" : s.timeToDetectMs() + " ms")).toList();
        return new ReportTable("Detection Coverage", r.createdAt(), r.createdAt(), summary,
                List.of("Scenario", "Expected rule", "MITRE", "Result", "Rules fired", "Alerts", "Time to detect"), rows, false);
    }

    private RunView view(CoverageRun r) {
        try {
            CoverageReport rep = objectMapper.readValue(r.getReport(), CoverageReport.class);
            return new RunView(r.getId(), r.getCreatedAt(), r.getCoveragePct().doubleValue(), r.getDetected(), r.getTested(), rep);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt coverage report " + r.getId(), e);
        }
    }

    private String write(CoverageReport r) {
        try {
            return objectMapper.writeValueAsString(r);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
