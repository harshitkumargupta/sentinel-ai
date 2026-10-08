package com.sentinelai.simulator.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.simulator.SimulatorProperties;
import com.sentinelai.simulator.SimulatorService;
import com.sentinelai.simulator.dto.SimulatorRunRequest;
import com.sentinelai.simulator.dto.SimulatorRunResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin-only; only registered when {@code sentinel.simulator.enabled=true}. */
@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(prefix = "sentinel.simulator", name = "enabled", havingValue = "true")
@Tag(name = "Simulator")
public class SimulatorController {

    private final SimulatorService simulatorService;
    private final SimulatorProperties properties;

    @PostMapping("/run")
    public ApiResponse<SimulatorRunResponse> run(@RequestBody(required = false) SimulatorRunRequest request,
                                                 @AuthenticationPrincipal AppUserPrincipal actor) {
        SimulatorRunRequest req = request != null ? request : new SimulatorRunRequest(null, null, null);
        long seed = req.seed() != null ? req.seed() : 42L;
        int intensity = req.intensity() != null ? req.intensity() : properties.getDefaultIntensity();
        var run = simulatorService.run(actor.getOrgId(), req.scenarios(), seed, intensity, req.timeAnchor());
        return ApiResponse.ok(SimulatorRunResponse.from(run));
    }

    @GetMapping("/runs")
    public ApiResponse<List<SimulatorRunResponse>> runs() {
        return ApiResponse.ok(simulatorService.recentRuns().stream()
                .map(SimulatorRunResponse::from).toList());
    }
}
