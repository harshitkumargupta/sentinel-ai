package com.sentinelai.site.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.site.SiteService;
import com.sentinelai.site.dto.SiteDtos.ApiKeyResponse;
import com.sentinelai.site.dto.SiteDtos.CreateSiteRequest;
import com.sentinelai.site.dto.SiteDtos.CreatedSiteResponse;
import com.sentinelai.site.dto.SiteDtos.SiteResponse;
import com.sentinelai.site.dto.SiteDtos.SnippetResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
@Tag(name = "Sites")
public class SiteController {

    private final SiteService siteService;

    // Any authenticated user sees the sites they have access to (for the site selector).
    @GetMapping
    public ApiResponse<List<SiteResponse>> list(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(siteService.listAccessible(actor));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CreatedSiteResponse> create(@Valid @RequestBody CreateSiteRequest request,
                                                   @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(siteService.create(actor, request.name(), request.domain()));
    }

    @PostMapping("/{id}/keys/rotate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ApiKeyResponse> rotateKey(@PathVariable Long id,
                                                 @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(siteService.rotateKey(actor, id));
    }

    @DeleteMapping("/keys/{keyId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> revokeKey(@PathVariable Long keyId,
                                       @AuthenticationPrincipal AppUserPrincipal actor) {
        siteService.revokeKey(actor, keyId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/snippet")
    public ApiResponse<SnippetResponse> snippet(@PathVariable Long id,
                                                @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(siteService.snippet(actor, id));
    }
}
