package com.sentinelai.demo.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.demo.DemoDataCleaner;
import com.sentinelai.demo.DemoService;
import com.sentinelai.demo.SampleDatasets;
import com.sentinelai.demo.SampleReplayService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Demo Center: one-click buttons over the existing simulator. Admin-only, and only registered when
 * both {@code sentinel.demo.enabled} and the simulator are on.
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(name = {"sentinel.demo.enabled", "sentinel.simulator.enabled"}, havingValue = "true")
@Tag(name = "Demo Center")
public class DemoController {

    private final DemoService demoService;
    private final SampleReplayService replayService;

    @GetMapping("/scenarios")
    public ApiResponse<List<DemoService.ScenarioView>> scenarios() {
        return ApiResponse.ok(demoService.scenarios());
    }

    @GetMapping("/chain")
    public ApiResponse<List<DemoService.ScenarioView>> chain() {
        return ApiResponse.ok(demoService.chain());
    }

    @PostMapping("/scenarios/{id}/run")
    public ApiResponse<DemoService.RunSummary> run(@PathVariable String id,
                                                   @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(demoService.run(actor.getOrgId(), id));
    }

    @PostMapping("/seed")
    public ApiResponse<DemoService.RunSummary> seed(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(demoService.seed(actor.getOrgId()));
    }

    @GetMapping("/datasets")
    public ApiResponse<List<SampleDatasets.Dataset>> datasets() {
        return ApiResponse.ok(replayService.datasets());
    }

    @PostMapping("/replay/{dataset}")
    public ApiResponse<SampleReplayService.ReplayResult> replay(@PathVariable String dataset,
                                                        @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(replayService.replay(actor.getOrgId(), actor.getUserId(), dataset));
    }

    @PostMapping("/reset")
    public ApiResponse<DemoDataCleaner.ResetResult> reset(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(demoService.reset(actor.getOrgId()));
    }
}
