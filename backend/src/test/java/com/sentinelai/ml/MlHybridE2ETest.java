package com.sentinelai.ml;

import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.simulator.SimulatorService;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/** Seeded end-to-end: with the model scoring, an incident's risk waterfall includes the ml_model factor. */
class MlHybridE2ETest extends IntegrationTestSupport {

    @MockBean
    private MlScoringClient mlClient;

    @Autowired private SimulatorService simulatorService;
    @Autowired private DetectionRuleRepository ruleRepository;
    @Autowired private AlertRepository alertRepository;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        ruleRepository.deleteAll();
        Mockito.when(mlClient.score(any(), eq("entity")))
                .thenReturn(Optional.of(new MlScore(80, "vTest", List.of())));
        Organization org = organizationRepository.findById(ORG_ID).orElseThrow();
        ruleRepository.save(DetectionRule.builder().org(org).name("bf").ruleType("BRUTE_FORCE")
                .config("{\"threshold\":10,\"windowSeconds\":300,\"groupBy\":\"username\"}")
                .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());
    }

    @Test
    void incidentWaterfallIncludesModelFactor() {
        simulatorService.run(ORG_ID, List.of("brute_force"), 42L, 1);

        assertThat(incidentRepository.count()).isEqualTo(1);
        String breakdown = incidentRepository.findAll().get(0).getRiskBreakdown();
        assertThat(breakdown).contains("ml_model");
        assertThat(breakdown).contains("vTest"); // model version recorded in the reason
    }
}
