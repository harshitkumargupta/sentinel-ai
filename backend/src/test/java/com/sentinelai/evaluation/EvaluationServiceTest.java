package com.sentinelai.evaluation;

import com.sentinelai.alert.domain.Alert;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.evaluation.dto.EvaluationResult;
import com.sentinelai.simulator.domain.SimLabel;
import com.sentinelai.simulator.repository.SimLabelRepository;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Exact precision/recall on a hand-built labeled run. */
class EvaluationServiceTest extends IntegrationTestSupport {

    @Autowired
    private EvaluationService evaluationService;
    @Autowired
    private SimLabelRepository simLabelRepository;
    @Autowired
    private AlertRepository alertRepository;

    private static final String RUN = "evaltest";
    private static final Instant T = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUpEval() {
        alertRepository.deleteAll();
        simLabelRepository.deleteAll();
    }

    private Long event(EventType type) {
        return securityEventRepository.save(SecurityEvent.builder()
                .org(organizationRepository.findById(ORG_ID).orElseThrow())
                .eventType(type).severity(Severity.LOW).honeytoken(false).eventTimestamp(T).build()).getId();
    }

    private void label(Long eventId, boolean attack, String expectedRule) {
        simLabelRepository.save(SimLabel.builder()
                .eventId(eventId).scenarioId("s").runId(RUN).attack(attack).expectedRule(expectedRule).build());
    }

    private void alert(String ruleType, Long trigger, String matchedJson) {
        alertRepository.save(Alert.builder()
                .org(organizationRepository.findById(ORG_ID).orElseThrow())
                .ruleType(ruleType).severity(Severity.HIGH).message("m")
                .triggeringEventId(trigger).matchedEventIds(matchedJson).runId(RUN).build());
    }

    @Test
    void computesExactPrecisionAndRecall() {
        Long e1 = event(EventType.FAILED_LOGIN);
        Long e2 = event(EventType.FAILED_LOGIN);
        Long e3 = event(EventType.HONEYTOKEN_ACCESS);
        Long e4 = event(EventType.OTHER);

        label(e1, true, "BRUTE_FORCE");
        label(e2, true, "BRUTE_FORCE");
        label(e3, true, "HONEYTOKEN");
        label(e4, false, null);

        alert("BRUTE_FORCE", e1, "[" + e1 + "," + e2 + "]");
        alert("HONEYTOKEN", e3, "[" + e3 + "]");
        alert("BRUTE_FORCE", e4, "[" + e4 + "]"); // false positive on a benign event

        EvaluationResult r = evaluationService.evaluate(RUN);

        // Overall: TP=e1,e2,e3 (3), FP=e4 (1), FN=0
        assertThat(r.overall().precision()).isEqualTo(0.75);
        assertThat(r.overall().recall()).isEqualTo(1.0);
        assertThat(r.overall().tp()).isEqualTo(3);
        assertThat(r.overall().fp()).isEqualTo(1);

        // BRUTE_FORCE: expected {e1,e2}, fired {e1,e2,e4} -> P=2/3, R=1
        assertThat(r.perRule().get("BRUTE_FORCE").precision()).isEqualTo(0.667);
        assertThat(r.perRule().get("BRUTE_FORCE").recall()).isEqualTo(1.0);
        // HONEYTOKEN: perfect
        assertThat(r.perRule().get("HONEYTOKEN").precision()).isEqualTo(1.0);
        assertThat(r.perRule().get("HONEYTOKEN").recall()).isEqualTo(1.0);
    }
}
