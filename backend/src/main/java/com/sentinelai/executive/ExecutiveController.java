package com.sentinelai.executive;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.report.PdfReportRenderer;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Executive dashboard data and the plain-language PDF (any role). */
@RestController
@RequestMapping("/api/executive")
@RequiredArgsConstructor
@Tag(name = "Executive")
public class ExecutiveController {

    private final ExecutiveService service;
    private final PdfReportRenderer pdf;

    @GetMapping
    public ApiResponse<ExecutiveService.Summary> summary(@RequestParam(defaultValue = "30") int days,
                                                         @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.summary(actor.getOrgId(), days));
    }

    @GetMapping("/report.pdf")
    public ResponseEntity<byte[]> report(@RequestParam(defaultValue = "30") int days, @AuthenticationPrincipal AppUserPrincipal actor) {
        byte[] body = pdf.render(service.table(service.summary(actor.getOrgId(), days)));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("sentinel-executive-summary.pdf").build().toString())
                .body(body);
    }
}
