package com.sentinelai.ai.web;

import com.sentinelai.ai.pipeline.InvestigationDispatcher;
import com.sentinelai.ai.pipeline.InvestigationService;
import com.sentinelai.ai.pipeline.InvestigationService.RequestResult;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI investigation workflow: request an async investigation, list/fetch analyses, and review them.
 * Investigation runs asynchronously (Kafka or a thread pool) and returns 202 with the analysis id;
 * poll the analysis endpoint for the result.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "AI investigation")
public class InvestigationController {

    private final InvestigationService investigationService;
    private final InvestigationDispatcher dispatcher;
    private final AnalysisService analysisService;

    @PostMapping("/api/incidents/{id}/investigate")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ResponseEntity<ApiResponse<AiDtos.InvestigateResponse>> investigate(
            @PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        RequestResult result = investigationService.request(id, actor);
        if (!result.reused()) {
            dispatcher.dispatch(result.analysisId());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(
                new AiDtos.InvestigateResponse(result.analysisId(), result.reused(),
                        result.reused() ? "COMPLETE" : "QUEUED")));
    }

    @GetMapping("/api/incidents/{id}/analysis")
    public ApiResponse<List<AiDtos.AnalysisResponse>> list(
            @PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(analysisService.listForIncident(id, actor));
    }

    @GetMapping("/api/analysis/{id}")
    public ApiResponse<AiDtos.AnalysisResponse> get(
            @PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(analysisService.get(id, actor));
    }

    @PostMapping("/api/analysis/{id}/review")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<AiDtos.AnalysisResponse> review(
            @PathVariable Long id, @Valid @RequestBody AiDtos.ReviewRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(analysisService.review(id, request.decision(), request.note(), actor));
    }
}
