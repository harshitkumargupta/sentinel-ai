package com.sentinelai.detection.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.detection.backtest.BacktestRequest;
import com.sentinelai.detection.backtest.BacktestResult;
import com.sentinelai.detection.backtest.BacktestService;
import com.sentinelai.detection.dto.CreateRuleRequest;
import com.sentinelai.detection.dto.EnabledRequest;
import com.sentinelai.detection.dto.RuleResponse;
import com.sentinelai.detection.dto.UpdateRuleRequest;
import com.sentinelai.detection.service.RuleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
@Tag(name = "Detection rules")
public class RuleController {

    private final RuleService ruleService;
    private final BacktestService backtestService;

    // Reading rules is allowed for any authenticated user (VIEWER+).
    @GetMapping
    public ApiResponse<List<RuleResponse>> list(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(ruleService.list(actor));
    }

    /** Rule types the engine can evaluate (one per registered evaluator). */
    @GetMapping("/types")
    public ApiResponse<java.util.Set<String>> types() {
        return ApiResponse.ok(ruleService.ruleTypes());
    }

    @GetMapping("/{id}")
    public ApiResponse<RuleResponse> get(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(ruleService.get(id, actor));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RuleResponse> create(@Valid @RequestBody CreateRuleRequest request,
                                            @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(ruleService.create(request, actor));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RuleResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateRuleRequest request,
                                            @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(ruleService.update(id, request, actor));
    }

    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RuleResponse> setEnabled(@PathVariable Long id,
                                                @Valid @RequestBody EnabledRequest request,
                                                @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(ruleService.setEnabled(id, request.enabled(), actor));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal AppUserPrincipal actor) {
        ruleService.delete(id, actor);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/backtest")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BacktestResult> backtest(@PathVariable Long id,
                                                @Valid @RequestBody BacktestRequest request,
                                                @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(backtestService.backtest(
                id, actor.getOrgId(), request.configOverride(), request.from(), request.to()));
    }
}
