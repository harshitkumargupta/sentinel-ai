package com.sentinelai.search.saved;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Saved searches (each user manages their own; any role) and pinned-widget stats. */
@RestController
@RequestMapping("/api/saved-searches")
@RequiredArgsConstructor
@Tag(name = "Saved searches")
public class SavedSearchController {

    private final SavedSearchService service;

    public record SaveRequest(@NotBlank @Size(max = 100) String name, @Size(max = 1000) String query,
                              @Valid SavedSearchService.Filters filters, boolean pinned) {
    }

    @GetMapping
    public ApiResponse<List<SavedSearchService.View>> list(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.list(actor));
    }

    @PostMapping
    public ApiResponse<SavedSearchService.View> create(@Valid @RequestBody SaveRequest r, @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.save(null, r.name(), r.query(), r.filters(), r.pinned(), actor));
    }

    @PutMapping("/{id}")
    public ApiResponse<SavedSearchService.View> update(@PathVariable Long id, @Valid @RequestBody SaveRequest r,
                                                       @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.save(id, r.name(), r.query(), r.filters(), r.pinned(), actor));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        service.delete(id, actor);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/stats")
    public ApiResponse<SavedSearchService.Stats> stats(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.stats(id, actor));
    }

    @GetMapping("/pinned")
    public ApiResponse<List<SavedSearchService.Stats>> pinned(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.pinnedStats(actor));
    }
}
