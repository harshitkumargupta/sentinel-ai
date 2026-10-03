package com.sentinelai.alert.web;

import com.sentinelai.alert.dto.AlertResponse;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.common.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts")
public class AlertController {

    private final AlertRepository alertRepository;

    @GetMapping
    public ApiResponse<PageResponse<AlertResponse>> list(
            @AuthenticationPrincipal AppUserPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = alertRepository.findByOrg_IdOrderByIdDesc(
                actor.getOrgId(), PageRequest.of(page, Math.min(size, 200)));
        return ApiResponse.ok(PageResponse.from(result, AlertResponse::from));
    }
}
