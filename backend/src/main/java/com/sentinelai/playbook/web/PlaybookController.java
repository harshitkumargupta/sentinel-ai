package com.sentinelai.playbook.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.common.web.RequestUtils;
import com.sentinelai.playbook.PlaybookProposalService;
import com.sentinelai.playbook.PlaybookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SOAR playbook endpoints. Coarse RBAC (ANALYST+) is enforced here; the stricter rules for
 * HIGH/CRITICAL actions (ADMIN approver, proposer≠approver, admin-risk guard) are enforced in the
 * service.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Playbooks")
public class PlaybookController {

    private final PlaybookService playbookService;
    private final PlaybookProposalService proposalService;

    @GetMapping("/api/incidents/{id}/actions")
    public ApiResponse<List<PlaybookActionResponse>> list(@PathVariable Long id,
                                                          @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(playbookService.listForIncident(id, actor));
    }

    @GetMapping("/api/incidents/{id}/action-targets")
    public ApiResponse<PlaybookProposalService.ActionTargets> targets(@PathVariable Long id,
                                                                     @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(proposalService.targets(id, actor));
    }

    @PostMapping("/api/incidents/{id}/actions")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<PlaybookActionResponse> propose(@PathVariable Long id,
                                                       @Valid @RequestBody ProposeActionRequest request,
                                                       @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(proposalService.propose(id, request, actor));
    }

    @PostMapping("/api/actions/{id}/dry-run")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<PlaybookActionResponse> dryRun(@PathVariable Long id,
                                                      @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(playbookService.dryRun(id, actor));
    }

    @PostMapping("/api/actions/{id}/approve")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<PlaybookActionResponse> approve(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "false") boolean stepUp,
                                                       @AuthenticationPrincipal AppUserPrincipal actor,
                                                       HttpServletRequest request) {
        return ApiResponse.ok(playbookService.approve(id, actor, RequestUtils.clientIp(request), stepUp));
    }

    @PostMapping("/api/actions/{id}/reject")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<PlaybookActionResponse> reject(@PathVariable Long id,
                                                      @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(playbookService.reject(id, actor));
    }

    @PostMapping("/api/actions/{id}/execute")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<PlaybookActionResponse> execute(@PathVariable Long id,
                                                       @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(playbookService.execute(id, actor));
    }

    @PostMapping("/api/actions/{id}/rollback")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<PlaybookActionResponse> rollback(@PathVariable Long id,
                                                        @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(playbookService.rollback(id, actor));
    }
}
