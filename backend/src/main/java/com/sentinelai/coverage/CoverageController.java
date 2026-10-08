package com.sentinelai.coverage;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.report.CsvReportRenderer;
import com.sentinelai.report.PdfReportRenderer;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Detection coverage: run (admin), history/view/export (analyst+). */
@RestController
@RequestMapping("/api/coverage")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
@Tag(name = "Detection coverage")
public class CoverageController {

    private final CoverageService service;
    private final PdfReportRenderer pdf;
    private final CsvReportRenderer csv;

    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CoverageService.RunView> run(@AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(service.run(a.getOrgId(), a.getUserId()));
    }

    @GetMapping("/runs")
    public ApiResponse<List<CoverageService.RunView>> runs(@AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(service.history(a.getOrgId()));
    }

    @GetMapping("/runs/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id, @RequestParam(defaultValue = "pdf") String format,
                                         @AuthenticationPrincipal AppUserPrincipal a) {
        var table = service.table(service.get(a.getOrgId(), id));
        boolean isPdf = !"csv".equalsIgnoreCase(format);
        byte[] body = isPdf ? pdf.render(table) : csv.render(table);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("sentinel-coverage-" + id + (isPdf ? ".pdf" : ".csv")).build().toString())
                .contentType(isPdf ? MediaType.APPLICATION_PDF : new MediaType("text", "csv"))
                .body(body);
    }
}
