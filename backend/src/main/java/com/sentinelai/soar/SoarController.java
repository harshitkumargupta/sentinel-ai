package com.sentinelai.soar;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** SOAR playbooks: read (any role), run (analyst+), define (admin). */
@RestController
@RequiredArgsConstructor
@Tag(name = "SOAR playbooks")
public class SoarController {

    private final SoarService service;

    public record SaveRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description,
                              @Size(max = 50) String triggerRuleType, Severity triggerMinSeverity,
                              @NotNull @Size(min = 1, max = 20) List<PlaybookStep> steps, Boolean enabled, Boolean autoRun) {
    }

    public record RunRequest(@NotNull Long incidentId) {
    }

    @GetMapping("/api/soar/playbooks")
    public ApiResponse<List<SoarService.PlaybookView>> list(@AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(service.list(a.getOrgId()));
    }

    @PostMapping("/api/soar/playbooks")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SoarService.PlaybookView> create(@Valid @RequestBody SaveRequest r, @AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(service.save(a.getOrgId(), a.getUserId(), null, r.name(), r.description(), r.triggerRuleType(),
                r.triggerMinSeverity(), r.steps(), r.enabled() == null || r.enabled(), Boolean.TRUE.equals(r.autoRun())));
    }

    @PutMapping("/api/soar/playbooks/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SoarService.PlaybookView> update(@PathVariable Long id, @Valid @RequestBody SaveRequest r,
                                                        @AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(service.save(a.getOrgId(), a.getUserId(), id, r.name(), r.description(), r.triggerRuleType(),
                r.triggerMinSeverity(), r.steps(), r.enabled() == null || r.enabled(), Boolean.TRUE.equals(r.autoRun())));
    }

    @DeleteMapping("/api/soar/playbooks/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal a) {
        service.delete(a.getOrgId(), a.getUserId(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/soar/playbooks/{id}/run")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<SoarService.RunView> run(@PathVariable Long id, @Valid @RequestBody RunRequest r,
                                                @AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(service.run(id, r.incidentId(), a));
    }

    @GetMapping("/api/soar/runs")
    public ApiResponse<List<SoarService.RunView>> runs(@AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(service.runs(a.getOrgId()));
    }

    @GetMapping("/api/incidents/{id}/playbooks")
    public ApiResponse<List<SoarService.PlaybookView>> matching(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(service.matching(id, a));
    }
}
