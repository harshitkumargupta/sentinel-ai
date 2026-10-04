package com.sentinelai.ai.eval;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
@Tag(name = "AI evaluation")
public class AiEvaluationController {

    private final AiEvaluationService aiEvaluationService;

    @GetMapping("/ai")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<AiEvaluationService.AiEvalResult> ai(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(aiEvaluationService.evaluate(actor.getOrgId()));
    }
}
