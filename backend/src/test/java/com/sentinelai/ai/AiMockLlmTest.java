package com.sentinelai.ai;

import com.sentinelai.ai.domain.AiAnalysis;
import com.sentinelai.ai.domain.ValidationStatus;
import com.sentinelai.ai.llm.LlmClient;
import com.sentinelai.ai.llm.LlmResponse;
import com.sentinelai.ai.llm.LlmUnavailableException;
import com.sentinelai.ai.nlsearch.NlSearchService;
import com.sentinelai.ai.pipeline.InvestigationService;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.incident.domain.Incident;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** AI enabled but the model misbehaves: unavailable → fallback, and an out-of-allow-list filter is
 *  rejected. */
@SpringBootTest(properties = {"sentinel.ai.enabled=true", "sentinel.ai.provider=fake"})
@ActiveProfiles("test")
class AiMockLlmTest extends AiTestSupport {

    @MockBean private LlmClient llm;

    @Autowired private InvestigationService investigationService;
    @Autowired private NlSearchService nlSearchService;

    @Test
    void llmUnavailableFallsBackToDeterministic() {
        when(llm.complete(any())).thenThrow(new LlmUnavailableException("timeout"));
        Incident incident = bruteForceIncident("mallory");
        Long id = investigationService.request(incident.getId(), principal()).analysisId();
        investigationService.run(id);

        AiAnalysis a = aiAnalysisRepository.findById(id).orElseThrow();
        assertThat(a.getValidationStatus()).isEqualTo(ValidationStatus.FALLBACK);
        assertThat(a.getModelName()).isEqualTo("deterministic");
        assertThat(a.getOutput()).contains("Deterministic summary");
    }

    @Test
    void nlSearchRejectsOutOfAllowListFilter() {
        when(llm.complete(any())).thenReturn(
                new LlmResponse("{\"filters\":{\"type\":\"HACKS\"}}", "fake-model", 1, 1, 1));
        assertThatThrownBy(() -> nlSearchService.search("show me hacks", principal()))
                .isInstanceOf(BadRequestException.class);
    }
}
