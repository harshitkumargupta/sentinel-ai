package com.sentinelai.incident.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.common.web.PageResponse;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.incident.dto.AssignRequest;
import com.sentinelai.incident.dto.IncidentDetailResponse;
import com.sentinelai.incident.dto.IncidentResponse;
import com.sentinelai.incident.dto.UpdateFeedbackRequest;
import com.sentinelai.incident.dto.UpdateStatusRequest;
import com.sentinelai.incident.service.IncidentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
@Tag(name = "Incidents")
public class IncidentController {

    private final IncidentService incidentService;

    @GetMapping
    public ApiResponse<PageResponse<IncidentResponse>> list(
            @AuthenticationPrincipal AppUserPrincipal actor,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.ok(PageResponse.from(
                incidentService.list(actor, status, severity, pageable), i -> i));
    }

    @GetMapping("/{id}")
    public ApiResponse<IncidentDetailResponse> get(@PathVariable Long id,
                                                   @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(incidentService.getDetail(id, actor));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<IncidentResponse> updateStatus(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateStatusRequest request,
                                                      @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(incidentService.updateStatus(id, request.status(), actor));
    }

    @PatchMapping("/{id}/feedback")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<IncidentResponse> updateFeedback(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateFeedbackRequest request,
                                                        @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(incidentService.updateFeedback(id, request.feedback(), actor));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<IncidentResponse> assign(@PathVariable Long id,
                                                @RequestBody AssignRequest request,
                                                @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(incidentService.assign(id, request.assigneeId(), actor));
    }
}
