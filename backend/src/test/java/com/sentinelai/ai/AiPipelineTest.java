package com.sentinelai.ai;

import com.sentinelai.ai.domain.AiAnalysis;
import com.sentinelai.ai.domain.AnalysisState;
import com.sentinelai.ai.domain.AnalysisStatus;
import com.sentinelai.ai.domain.ValidationStatus;
import com.sentinelai.ai.nlsearch.NlSearchResponse;
import com.sentinelai.ai.nlsearch.NlSearchService;
import com.sentinelai.ai.pipeline.InvestigationService;
import com.sentinelai.ai.web.AiDtos;
import com.sentinelai.ai.web.AnalysisService;
import com.sentinelai.audit.repository.AuditLogRepository;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.incident.domain.Incident;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end AI pipeline with the deterministic fake model: valid analysis, idempotency, injection
 *  flagging, review → playbook, and safe NL search. */
@SpringBootTest(properties = {"sentinel.ai.enabled=true", "sentinel.ai.provider=fake"})
@ActiveProfiles("test")
class AiPipelineTest extends AiTestSupport {

    @Autowired private InvestigationService investigationService;
    @Autowired private AnalysisService analysisService;
    @Autowired private NlSearchService nlSearchService;
    @Autowired private AuditLogRepository auditLogRepository;

    private AiAnalysis investigate(Incident incident) {
        Long id = investigationService.request(incident.getId(), principal()).analysisId();
        investigationService.run(id);
        return aiAnalysisRepository.findById(id).orElseThrow();
    }

    @Test
    void producesValidEvidenceBackedAnalysis() {
        AiAnalysis a = investigate(bruteForceIncident("mallory"));
        assertThat(a.getAnalysisState()).isEqualTo(AnalysisState.COMPLETE);
        assertThat(a.getValidationStatus()).isEqualTo(ValidationStatus.VALID);
        assertThat(a.getFaithfulnessScore().doubleValue()).isGreaterThan(0.0);
        assertThat(a.getModelName()).isEqualTo("fake-model");
        assertThat(a.getEvidenceEventIds()).contains("[");
    }

    @Test
    void reInvestigationIsIdempotent() {
        Incident incident = bruteForceIncident("oscar");
        Long first = investigationService.request(incident.getId(), principal()).analysisId();
        investigationService.run(first);
        var second = investigationService.request(incident.getId(), principal());
        assertThat(second.reused()).isTrue();
        assertThat(second.analysisId()).isEqualTo(first);
    }

    @Test
    void injectionIsFlaggedAndNotFollowed() {
        Incident incident = bruteForceIncident("mallory");
        addInjectionEvent(incident);
        AiAnalysis a = investigate(incident);

        assertThat(a.isInjectionDetected()).isTrue();
        assertThat(securityEventRepository.findAll())
                .anyMatch(e -> e.getEventType() == EventType.PROMPT_INJECTION);
        // The model's output never adopts the injected instruction (no "system prompt" leakage,
        // no disallowed action, no false-positive marking).
        assertThat(a.getOutput().toLowerCase()).doesNotContain("system prompt");
        assertThat(a.getOutput().toLowerCase()).doesNotContain("false positive");
    }

    @Test
    void approvingReviewCreatesPlaybookActionsAndAuditLog() {
        AiAnalysis a = investigate(bruteForceIncident("trudy"));
        analysisService.review(a.getId(), AiDtos.ReviewRequest.Decision.APPROVE, "looks right", principal());

        assertThat(aiAnalysisRepository.findById(a.getId()).orElseThrow().getStatus())
                .isEqualTo(AnalysisStatus.APPROVED);
        assertThat(playbookActionRepository.findByIncident_Id(a.getIncident().getId())).isNotEmpty();
        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> "AI_ANALYSIS_REVIEW".equals(l.getAction()));
    }

    @Test
    void nlSearchMapsToValidatedFilter() {
        bruteForceIncident("mallory");
        NlSearchResponse r = nlSearchService.search("critical brute force from 203.0.113.5", principal());
        assertThat(r.interpretedFilter()).containsEntry("ip", "203.0.113.5");
        assertThat(r.interpretedFilter()).containsEntry("type", "BRUTE_FORCE");
        assertThat(r.interpretedFilter()).containsEntry("severity", "CRITICAL");
    }

    @Test
    void nlSearchRejectsSqlLikeInput() {
        assertThatThrownBy(() -> nlSearchService.search("SELECT * FROM users; DROP TABLE events", principal()))
                .isInstanceOf(BadRequestException.class);
    }
}
