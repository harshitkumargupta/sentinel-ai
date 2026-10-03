package com.sentinelai.audit.web;

import com.sentinelai.audit.dto.AuditLogResponse;
import com.sentinelai.audit.dto.AuditVerifyResult;
import com.sentinelai.audit.repository.AuditLogRepository;
import com.sentinelai.audit.service.AuditService;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.common.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Audit")
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    @GetMapping
    public ApiResponse<PageResponse<AuditLogResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = auditLogRepository.findAllByOrderByIdDesc(PageRequest.of(page, Math.min(size, 200)));
        return ApiResponse.ok(PageResponse.from(result, AuditLogResponse::from));
    }

    @GetMapping("/verify")
    public ApiResponse<AuditVerifyResult> verify() {
        Long firstBroken = auditService.verifyChain();
        return ApiResponse.ok(new AuditVerifyResult(firstBroken == null, firstBroken));
    }
}
