package com.sentinelai.ai;

import com.sentinelai.ai.eval.AiEvaluationService;
import com.sentinelai.ai.eval.AiEvaluationService.AiEvalResult;
import com.sentinelai.ai.pipeline.InvestigationService;
import com.sentinelai.incident.domain.Incident;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** AI evaluation metrics over a controlled set of investigations (fake model). */
@SpringBootTest(properties = {"sentinel.ai.enabled=true", "sentinel.ai.provider=fake"})
@ActiveProfiles("test")
class AiEvaluationTest extends AiTestSupport {

    @Autowired private InvestigationService investigationService;
    @Autowired private AiEvaluationService aiEvaluationService;

    private void investigate(Incident incident) {
        Long id = investigationService.request(incident.getId(), principal()).analysisId();
        investigationService.run(id);
    }

    @Test
    void computesFaithfulnessValidityAndInjectionMetrics() {
        investigate(bruteForceIncident("alice"));
        investigate(bruteForceIncident("bob"));
        Incident injected = bruteForceIncident("mallory");
        addInjectionEvent(injected);
        investigate(injected);

        AiEvalResult r = aiEvaluationService.evaluate(ORG_ID);
        System.out.printf("AI-EVAL count=%d avgFaithfulness=%.2f pctValid=%.2f pctFallback=%.2f "
                        + "injectionFlagged=%d injectionPassRate=%.2f medianLatencyMs=%d%n",
                r.count(), r.avgFaithfulness(), r.pctValid(), r.pctFallback(),
                r.injectionFlagged(), r.injectionPassRate(), r.medianLatencyMs());

        assertThat(r.count()).isEqualTo(3);
        assertThat(r.avgFaithfulness()).isGreaterThan(0.0);
        assertThat(r.pctValid()).isEqualTo(1.0); // fake model always cites real evidence
        assertThat(r.injectionFlagged()).isGreaterThanOrEqualTo(1);
        assertThat(r.injectionPassRate()).isEqualTo(1.0);
    }
}
