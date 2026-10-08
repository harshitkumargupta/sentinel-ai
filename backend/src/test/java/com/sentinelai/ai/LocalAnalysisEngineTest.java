package com.sentinelai.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.ai.assistant.AskIntent;
import com.sentinelai.ai.assistant.AssistantAnswer;
import com.sentinelai.ai.assistant.LocalIncidentAssistant;
import com.sentinelai.ai.context.IncidentContext;
import com.sentinelai.ai.context.IncidentContext.EventSummary;
import com.sentinelai.ai.local.LocalAnalysisEngine;
import com.sentinelai.ai.pipeline.AnalysisOutput;
import com.sentinelai.ai.validation.EvidenceValidator;
import com.sentinelai.common.mitre.MitreCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The offline engine builds every report section from the context and passes evidence validation. */
class LocalAnalysisEngineTest {

    private final LocalAnalysisEngine engine = new LocalAnalysisEngine(new MitreCatalog(new ObjectMapper()));
    private final EvidenceValidator validator = new EvidenceValidator(new AiProperties());

    private IncidentContext context() {
        List<EventSummary> events = List.of(
                ev(1, "FAILED_LOGIN", "45.33.10.20", "victim", null, "CN", "2026-10-08T10:00:00Z"),
                ev(2, "FAILED_LOGIN", "45.33.10.20", "victim", null, "CN", "2026-10-08T10:00:10Z"),
                ev(3, "LOGIN_SUCCESS", "45.33.10.20", "victim", null, "CN", "2026-10-08T10:01:00Z"),
                ev(4, "MALWARE_DETECTED", "10.20.1.5", "victim", "WS-FIN-023", "US", "2026-10-08T10:05:00Z"));
        return new IncidentContext(7L, 1L, "HIGH", 82, new IncidentContext.Entity("user", "victim"),
                List.of("T1110", "T1204.002"),
                List.of(new IncidentContext.RiskFactor("severity", 30, "HIGH alerts")),
                events, List.of(), List.of(1L, 2L, 3L, 4L));
    }

    private static EventSummary ev(long id, String type, String ip, String user, String host, String country, String ts) {
        return new EventSummary(id, type, "LOW", ip, user, null, null, country, ts, host);
    }

    @Test
    void producesFullReportGroundedInEvidence() {
        IncidentContext ctx = context();
        AnalysisOutput out = engine.analyze(ctx);

        assertThat(out.summary()).contains("Incident #7").contains("failed login").contains("WS-FIN-023");
        AnalysisOutput.Report r = out.report();
        assertThat(r.timeline()).hasSize(3); // FAILED_LOGIN, LOGIN_SUCCESS, MALWARE_DETECTED stages
        assertThat(r.affected().ips()).containsExactly("45.33.10.20", "10.20.1.5");
        assertThat(r.affected().hosts()).containsExactly("WS-FIN-023");
        assertThat(r.mitre()).extracting(AnalysisOutput.MitreMapping::name)
                .contains("Brute Force", "User Execution: Malicious File");
        assertThat(r.severityReasoning()).contains("82/100").contains("severity (+30");
        assertThat(out.hypotheses()).anyMatch(h -> h.contains("Credential compromise"));

        // Recommendations: external IP only (never the internal one), the host, the user.
        assertThat(out.recommendations()).extracting(AnalysisOutput.Recommendation::action)
                .contains("block_ip", "isolate_host", "force_password_reset");
        assertThat(out.recommendations()).filteredOn(rec -> rec.action().equals("block_ip"))
                .extracting(AnalysisOutput.Recommendation::target).containsExactly("45.33.10.20");

        assertThat(validator.validate(out, ctx).valid()).isTrue();
        assertThat(validator.validate(out, ctx).faithfulness()).isEqualTo(1.0);
    }

    @Test
    void emptyIncidentYieldsSafeOutput() {
        IncidentContext empty = new IncidentContext(1L, 1L, "LOW", 0, null, List.of(), List.of(),
                List.of(), List.of(), List.of());
        AnalysisOutput out = engine.analyze(empty);
        assertThat(out.recommendations()).isEmpty();
        assertThat(out.summary()).contains("No events");
    }

    @Test
    void assistantAnswersEachIntentAndRoutesFreeText() {
        LocalIncidentAssistant assistant = new LocalIncidentAssistant(engine);
        IncidentContext ctx = context();

        for (AskIntent intent : AskIntent.values()) {
            AssistantAnswer a = assistant.answer(ctx, intent, null);
            assertThat(a.answer()).isNotBlank();
            assertThat(a.offline()).isTrue();
        }
        assertThat(assistant.answer(ctx, AskIntent.WHICH_IPS, null).bullets())
                .anyMatch(b -> b.startsWith("45.33.10.20 (CN, external)"));
        assertThat(AskIntent.route("Which MITRE techniques apply?")).contains(AskIntent.WHICH_MITRE);
        assertThat(AskIntent.route("what should I do now")).contains(AskIntent.WHAT_TO_DO);
        assertThat(AskIntent.route("tell me a joke")).isEmpty();
        assertThat(assistant.answer(ctx, null, "tell me a joke").answer()).contains("I can answer");
    }
}
