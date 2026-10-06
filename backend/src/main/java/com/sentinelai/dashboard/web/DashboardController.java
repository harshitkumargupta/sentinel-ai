package com.sentinelai.dashboard.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.dashboard.dto.AlertReductionResponse;
import com.sentinelai.dashboard.dto.DashboardSummary;
import com.sentinelai.dashboard.dto.MitreCoverageItem;
import com.sentinelai.dashboard.service.DashboardService;

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummary> summary(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(dashboardService.summary(actor));
    }

    @GetMapping("/alert-reduction")
    public ApiResponse<AlertReductionResponse> alertReduction(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(dashboardService.alertReduction(actor));
    }

    @GetMapping("/mitre-coverage")
    public ApiResponse<List<MitreCoverageItem>> mitreCoverage(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(dashboardService.mitreCoverage(actor));
    }

    @GetMapping("/geo-flows")
    public ApiResponse<List<com.sentinelai.dashboard.dto.GeoFlowItem>> geoFlows(
            @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(dashboardService.geoFlows(actor));
    }
}
