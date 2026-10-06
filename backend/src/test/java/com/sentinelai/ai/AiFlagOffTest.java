package com.sentinelai.ai;

import com.sentinelai.ai.domain.AiAnalysis;
import com.sentinelai.ai.domain.ValidationStatus;
import com.sentinelai.ai.nlsearch.NlSearchService;
import com.sentinelai.ai.pipeline.InvestigationService;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.incident.domain.Incident;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** With AI disabled, investigation still works but always returns a deterministic FALLBACK, and NL
 *  search returns a clear error (no LLM is contacted). */
@SpringBootTest(properties = "sentinel.ai.enabled=false")
@ActiveProfiles("test")
class AiFlagOffTest extends AiTestSupport {

    @Autowired private InvestigationService investigationService;
    @Autowired private NlSearchService nlSearchService;

    @Test
    void investigationFallsBackWhenAiDisabled() {
        Incident incident = bruteForceIncident("mallory");
        Long id = investigationService.request(incident.getId(), principal()).analysisId();
        investigationService.run(id);

        AiAnalysis a = aiAnalysisRepository.findById(id).orElseThrow();
        assertThat(a.getValidationStatus()).isEqualTo(ValidationStatus.FALLBACK);
        assertThat(a.getModelName()).isEqualTo("deterministic");
    }

    @Test
    void nlSearchReturnsClearErrorWhenAiDisabled() {
        assertThatThrownBy(() -> nlSearchService.search("critical events", principal()))
                .isInstanceOf(BadRequestException.class);
    }
}
