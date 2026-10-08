package com.sentinelai.honeytoken.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.honeytoken.HoneytokenService;
import com.sentinelai.honeytoken.domain.HoneytokenKind;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Honeytokens: view (analyst+), create/delete/test (admin). */
@RestController
@RequestMapping("/api/honeytokens")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
@Tag(name = "Honeytokens")
public class HoneytokenController {

    private final HoneytokenService service;

    public record CreateRequest(@NotNull HoneytokenKind kind, @Size(max = 200) String value, @Size(max = 255) String description) {
    }

    public record TestRequest(@Size(max = 200) String value) {
    }

    public record TestResult(Long eventId) {
    }

    @GetMapping
    public ApiResponse<List<HoneytokenService.View>> list(@AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(service.list(a.getOrgId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<HoneytokenService.Created> create(@Valid @RequestBody CreateRequest r, @AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(service.create(a, r.kind(), r.value(), r.description()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal a) {
        service.delete(a, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TestResult> test(@PathVariable Long id, @Valid @RequestBody(required = false) TestRequest r,
                                        @AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(new TestResult(service.test(a, id, r == null ? null : r.value())));
    }
}
