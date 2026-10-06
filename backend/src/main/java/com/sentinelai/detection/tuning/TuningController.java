package com.sentinelai.detection.tuning;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Detection-tuning suggestions from analyst feedback. Advisory only; applying is the normal rule edit. */
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
@Tag(name = "Rule tuning")
public class TuningController {

    private final TuningService tuningService;

    @GetMapping("/tuning-suggestions")
    public ApiResponse<List<RuleTuning>> all(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(tuningService.forOrg(actor.getOrgId()));
    }

    @GetMapping("/{id}/tuning-suggestions")
    public ApiResponse<RuleTuning> one(@PathVariable Long id,
                                       @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(tuningService.forRule(id, actor.getOrgId()));
    }
}
