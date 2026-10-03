package com.sentinelai.adminrisk.web;

import com.sentinelai.adminrisk.AdminApprovalService;
import com.sentinelai.adminrisk.AdminGuardService;
import com.sentinelai.adminrisk.GuardDecision;
import com.sentinelai.adminrisk.web.AdminDtos.PendingActionResponse;
import com.sentinelai.adminrisk.web.AdminDtos.SessionResponse;
import com.sentinelai.audit.dto.AuditLogResponse;
import com.sentinelai.audit.repository.AuditLogRepository;
import com.sentinelai.auth.repository.RefreshTokenRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.auth.service.UserService;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.common.web.PageResponse;
import com.sentinelai.common.web.RequestUtils;
import com.sentinelai.ingestion.enrich.GeoIpEnricher;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin risk")
public class AdminController {

    private final AdminGuardService guardService;
    private final AdminApprovalService approvalService;
    private final UserService userService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final GeoIpEnricher geoIpEnricher;

    /** Risk-gated user disable. Executes only when the guard returns ALLOW. */
    @PostMapping("/actions/disable-user/{id}")
    public ApiResponse<GuardDecision> disableUser(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "false") boolean stepUp,
                                                  @AuthenticationPrincipal AppUserPrincipal actor,
                                                  HttpServletRequest request) {
        String ip = RequestUtils.clientIp(request);
        String country = geoIpEnricher.country(ip).orElse(null);
        String device = request.getHeader("User-Agent");
        GuardDecision decision = guardService.guard(actor, "USER_DISABLE", "user", id,
                "{\"enabled\":true}", "{\"enabled\":false}", ip, country, device, null, stepUp);
        if (decision.allowed()) {
            userService.disable(id, actor);
        }
        return ApiResponse.ok(decision);
    }

    @GetMapping("/pending")
    public ApiResponse<List<PendingActionResponse>> pending(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(approvalService.listPending(actor.getOrgId()).stream()
                .map(PendingActionResponse::from).toList());
    }

    @PostMapping("/pending/{id}/approve")
    public ApiResponse<PendingActionResponse> approve(@PathVariable Long id,
                                                      @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(PendingActionResponse.from(approvalService.approve(id, actor)));
    }

    @PostMapping("/pending/{id}/reject")
    public ApiResponse<PendingActionResponse> reject(@PathVariable Long id,
                                                     @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(PendingActionResponse.from(approvalService.reject(id, actor)));
    }

    @GetMapping("/sessions/{userId}")
    public ApiResponse<List<SessionResponse>> sessions(@PathVariable Long userId) {
        return ApiResponse.ok(refreshTokenRepository.findByUser_IdOrderByIdDesc(userId).stream()
                .map(SessionResponse::from).toList());
    }

    @PostMapping("/sessions/{userId}/revoke")
    public ApiResponse<Integer> revokeSessions(@PathVariable Long userId) {
        return ApiResponse.ok(refreshTokenRepository.revokeAllForUser(userId)); // force logout
    }

    @GetMapping("/timeline")
    public ApiResponse<PageResponse<AuditLogResponse>> timeline(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        var result = auditLogRepository.timeline(actorId, action, entityType, from,
                PageRequest.of(page, Math.min(size, 200)));
        return ApiResponse.ok(PageResponse.from(result, AuditLogResponse::from));
    }
}
