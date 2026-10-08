package com.sentinelai.logsource.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.logsource.LogSourceService;
import com.sentinelai.logsource.web.LogSourceDtos.CreateLogSourceRequest;
import com.sentinelai.logsource.web.LogSourceDtos.CreatedLogSource;
import com.sentinelai.logsource.web.LogSourceDtos.LogSourceView;
import com.sentinelai.logsource.web.LogSourceDtos.SetEnabledRequest;
import com.sentinelai.site.dto.SiteDtos.ApiKeyResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Log Sources: list (analyst+), create / enable / rotate key / delete (admin). */
@RestController
@RequestMapping("/api/log-sources")
@RequiredArgsConstructor
@Tag(name = "Log sources")
public class LogSourceController {

    private final LogSourceService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<List<LogSourceView>> list(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.list(actor));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CreatedLogSource> create(@Valid @RequestBody CreateLogSourceRequest request,
                                                @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.create(actor, request));
    }

    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LogSourceView> setEnabled(@PathVariable Long id, @Valid @RequestBody SetEnabledRequest request,
                                                 @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.setEnabled(actor, id, request.enabled()));
    }

    @PostMapping("/{id}/keys/rotate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ApiKeyResponse> rotate(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.rotateKey(actor, id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        service.delete(actor, id);
        return ApiResponse.ok(null);
    }
}
