package com.sentinelai.ai.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.ai.domain.AiAnalysis;
import com.sentinelai.ai.domain.AnalysisStatus;
import com.sentinelai.ai.pipeline.AnalysisOutput;
import com.sentinelai.ai.repository.AiAnalysisRepository;
import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.playbook.PlaybookProperties;
import com.sentinelai.playbook.PlaybookService;
import com.sentinelai.playbook.domain.PlaybookAction;
import com.sentinelai.playbook.domain.PlaybookActionStatus;
import com.sentinelai.playbook.repository.PlaybookActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Lists, fetches and reviews AI analyses; approved recommendations become PROPOSED playbook actions. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AiAnalysisRepository analysisRepository;
    private final PlaybookActionRepository playbookActionRepository;
    private final PlaybookProperties playbookProperties;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AiDtos.AnalysisResponse> listForIncident(Long incidentId, AppUserPrincipal actor) {
        return analysisRepository.findByIncident_IdOrderByIdDesc(incidentId).stream()
                .filter(a -> a.getIncident().getOrg().getId().equals(actor.getOrgId()))
                .map(a -> AiDtos.AnalysisResponse.from(a, objectMapper))
                .toList();
    }

    @Transactional(readOnly = true)
    public AiDtos.AnalysisResponse get(Long analysisId, AppUserPrincipal actor) {
        return AiDtos.AnalysisResponse.from(loadScoped(analysisId, actor), objectMapper);
    }

    @Transactional
    public AiDtos.AnalysisResponse review(Long analysisId, AiDtos.ReviewRequest.Decision decision,
                                          String note, AppUserPrincipal actor) {
        AiAnalysis analysis = loadScoped(analysisId, actor);
        AnalysisStatus status = switch (decision) {
            case APPROVE -> AnalysisStatus.APPROVED;
            case REJECT -> AnalysisStatus.REJECTED;
            case MODIFY -> AnalysisStatus.MODIFIED;
        };
        analysis.setStatus(status);
        analysis.setReviewedBy(userRepository.getReferenceById(actor.getUserId()));
        analysis.setReviewNote(truncate(note));
        analysisRepository.save(analysis);

        auditService.record(actor.getOrgId(), actor.getUserId(), "AI_ANALYSIS_REVIEW",
                "ai_analysis", analysisId, "{\"decision\":\"" + decision + "\"}", null);

        if (decision == AiDtos.ReviewRequest.Decision.APPROVE) {
            proposeActions(analysis, actor);
        }
        return AiDtos.AnalysisResponse.from(analysis, objectMapper);
    }

    /**
     * Turn the approved analysis' recommendations into PROPOSED playbook actions (execution is a
     * separate, human-approved step). The reviewing analyst becomes the proposer; the action's risk
     * level is the incident severity, and the proposal expires after the configured window.
     */
    private void proposeActions(AiAnalysis analysis, AppUserPrincipal actor) {
        if (analysis.getOutput() == null) {
            return;
        }
        AnalysisOutput output;
        try {
            output = objectMapper.readValue(analysis.getOutput(), AnalysisOutput.class);
        } catch (Exception e) {
            log.warn("Could not parse analysis {} output for playbook proposal", analysis.getId());
            return;
        }
        if (output.recommendations() == null) {
            return;
        }
        var expiresAt = clock.instant().plus(playbookProperties.getExpiryMinutes(), ChronoUnit.MINUTES);
        for (AnalysisOutput.Recommendation r : output.recommendations()) {
            playbookActionRepository.save(PlaybookAction.builder()
                    .incident(analysis.getIncident())
                    .proposedBy(userRepository.getReferenceById(actor.getUserId()))
                    .actionType(PlaybookService.actionTypeFor(r.action()))
                    .targetRef(r.target())
                    .reason(r.reason())
                    .analysisId(analysis.getId())
                    .riskLevel(analysis.getIncident().getSeverity())
                    .expiresAt(expiresAt)
                    .status(PlaybookActionStatus.PROPOSED)
                    .build());
        }
    }

    private AiAnalysis loadScoped(Long analysisId, AppUserPrincipal actor) {
        return analysisRepository.findById(analysisId)
                .filter(a -> a.getIncident().getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Analysis not found: " + analysisId));
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
