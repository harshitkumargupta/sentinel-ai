package com.sentinelai.asset.web;

import com.sentinelai.asset.AssetService;
import com.sentinelai.asset.web.AssetDtos.AssetDetail;
import com.sentinelai.asset.web.AssetDtos.AssetView;
import com.sentinelai.asset.web.AssetDtos.ImportResult;
import com.sentinelai.asset.web.AssetDtos.SaveAssetRequest;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Asset inventory: read (any role), create/update/import (analyst+), delete (admin). */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@Tag(name = "Assets")
public class AssetController {

    private final AssetService service;

    @GetMapping
    public ApiResponse<List<AssetView>> list(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.list(actor));
    }

    @GetMapping("/{id}")
    public ApiResponse<AssetDetail> get(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.get(id, actor));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<AssetView> create(@Valid @RequestBody SaveAssetRequest req, @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.save(null, req, actor));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<AssetView> update(@PathVariable Long id, @Valid @RequestBody SaveAssetRequest req,
                                         @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.save(id, req, actor));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        service.delete(id, actor);
        return ApiResponse.ok(null);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<ImportResult> importCsv(@RequestParam("file") MultipartFile file,
                                               @AuthenticationPrincipal AppUserPrincipal actor) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (file.isEmpty() || !(name.endsWith(".csv") || name.endsWith(".txt"))) {
            throw new BadRequestException("Upload a non-empty .csv file");
        }
        return ApiResponse.ok(service.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8).lines().toList(), actor));
    }

    @PostMapping("/relink")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Integer> relink(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.relink(actor.getOrgId()));
    }
}
