package com.sentinelai.coverage;

import java.util.List;

/** Result of one coverage run: per-scenario outcome, the MITRE matrix, gaps, and the score. */
public record CoverageReport(double coveragePct, int detected, int tested, List<ScenarioResult> scenarios,
                             List<MatrixCell> matrix, List<Gap> gaps) {

    public enum CellStatus { DETECTED, MISSED, UNTESTED }

    /** {@code timeToDetectMs}: from the scenario's first event ingest to its first alert (null if missed). */
    public record ScenarioResult(String scenario, String label, String expectedRule, String technique,
                                 boolean detected, List<String> rulesFired, int alerts, Long timeToDetectMs, int events) {
    }

    public record MatrixCell(String technique, String name, String tactic, CellStatus status, String detail) {
    }

    public record Gap(String item, String reason, String suggestion) {
    }
}
