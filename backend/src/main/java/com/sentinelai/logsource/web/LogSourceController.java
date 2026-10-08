package com.sentinelai.logsource.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.ingestion.parse.LogFormat;
import com.sentinelai.ingestion.parse.LogIngestService;
import com.sentinelai.logsource.LogSourceService;
import com.sentinelai.logsource.web.LogSourceDtos.CreateLogSourceRequest;
import com.sentinelai.logsource.web.LogSourceDtos.CreatedLogSource;
import com.sentinelai.logsource.web.LogSourceDtos.LogSourceView;
import com.sentinelai.logsource.web.LogSourceDtos.SetEnabledRequest;
import com.sentinelai.site.dto.SiteDtos.ApiKeyResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Log Sources: list + upload (analyst+), create / enable / rotate key / delete (admin). */
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

    /** Upload a log file (≤ the multipart limit) for a source; {@code format} defaults from its type. */
    @PostMapping(value = "/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<LogIngestService.Report> upload(@PathVariable Long id,
                                                       @RequestParam("file") MultipartFile file,
                                                       @RequestParam(value = "format", required = false) LogFormat format,
                                                       @AuthenticationPrincipal AppUserPrincipal actor) throws IOException {
        if (file.isEmpty()) {
            throw new BadRequestException("The uploaded file is empty");
        }
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        }
        return ApiResponse.ok(service.upload(actor, id, format, lines));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        service.delete(actor, id);
        return ApiResponse.ok(null);
    }
}
