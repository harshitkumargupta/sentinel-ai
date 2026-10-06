package com.sentinelai.incident;

import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.evaluation.EvaluationService;
import com.sentinelai.evaluation.dto.EvaluationResult;
import com.sentinelai.simulator.SimulatorService;
import com.sentinelai.simulator.domain.SimulatorRun;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Simulated brute force -> alerts -> exactly one incident, scored and escalated. */
class FullScenarioTest extends IntegrationTestSupport {

    @Autowired private SimulatorService simulatorService;
    @Autowired private EvaluationService evaluationService;
    @Autowired private DetectionRuleRepository ruleRepository;
    @Autowired private AlertRepository alertRepository;

    @BeforeEach
    void setUpScenario() {
        alertRepository.deleteAll();
        ruleRepository.deleteAll();
        Organization org = organizationRepository.findById(ORG_ID).orElseThrow();
        ruleRepository.save(DetectionRule.builder().org(org).name("bf").ruleType("BRUTE_FORCE")
                .config("{\"threshold\":10,\"windowSeconds\":300,\"groupBy\":\"username\"}")
                .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());
    }

    @Test
    void bruteForceScenarioProducesOneScoredIncident() {
        SimulatorRun run = simulatorService.run(ORG_ID, List.of("brute_force"), 42L, 1);

        // Exactly one incident for the single attack scenario.
        assertThat(incidentRepository.count()).isEqualTo(1);
        var incident = incidentRepository.findAll().get(0);
        assertThat(incident.getRiskScore()).isGreaterThan(0);
        assertThat(incident.getSeverity()).isIn(Severity.MEDIUM, Severity.HIGH, Severity.CRITICAL);

        EvaluationResult eval = evaluationService.evaluate(run.getRunId());
        assertThat(eval.incidentLevel().attackScenarios()).isEqualTo(1);
        assertThat(eval.incidentLevel().detectedScenarios()).isEqualTo(1);
        assertThat(eval.incidentLevel().exactlyOneScenarios()).isEqualTo(1);
        // Alert reduction: 12 brute-force events collapse to a single incident.
        assertThat(eval.alertReduction().incidents()).isEqualTo(1);
        assertThat(eval.alertReduction().events()).isGreaterThan(eval.alertReduction().incidents());
    }
}
