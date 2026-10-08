package com.sentinelai.site.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.site.SiteConnectService;
import com.sentinelai.site.dto.SiteDtos.CreatedSiteResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Connect My Website wizard: register + key (admin), connection status (analyst+), availability check (admin). */
@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
@Tag(name = "Sites")
public class SiteConnectController {

    private final SiteConnectService service;

    public record ConnectRequest(@NotBlank @Size(max = 150) String name,
                                 @NotBlank @Size(max = 255) String url,
                                 @AssertTrue(message = "confirm you own or are authorized to monitor this site") boolean authorized,
                                 @NotNull SiteConnectService.Method method) {
    }

    @PostMapping("/connect")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CreatedSiteResponse> connect(@Valid @RequestBody ConnectRequest r, @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.connect(actor, r.name(), r.url(), r.authorized(), r.method()));
    }

    @GetMapping("/{id}/connection")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<SiteConnectService.Connection> connection(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.connection(actor, id));
    }

    @PostMapping("/{id}/monitor-check")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SiteConnectService.MonitorResult> monitorCheck(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.monitorCheck(actor, id));
    }
}
