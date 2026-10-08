package com.sentinelai.detection.buildingblock;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.detection.buildingblock.BuildingBlockDtos.BuildingBlockView;
import com.sentinelai.detection.buildingblock.BuildingBlockDtos.SaveBuildingBlockRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** Building blocks: read for any role, edit for ADMIN. */
@RestController
@RequestMapping("/api/building-blocks")
@RequiredArgsConstructor
@Tag(name = "Building blocks")
public class BuildingBlockController {

    private final BuildingBlockService service;

    public record Vocabulary(List<String> fields, List<String> operators) {
    }

    @GetMapping
    public ApiResponse<List<BuildingBlockView>> list(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.list(actor));
    }

    @GetMapping("/vocabulary")
    public ApiResponse<Vocabulary> vocabulary() {
        return ApiResponse.ok(new Vocabulary(
                List.of("sourceIp", "username", "eventType", "outcome", "severity", "resource", "geoCountry",
                        "userAgent", "hour"),
                Arrays.stream(ConditionOperator.values()).map(Enum::name).toList()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BuildingBlockView> create(@Valid @RequestBody SaveBuildingBlockRequest request,
                                                 @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.create(request, actor));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BuildingBlockView> update(@PathVariable Long id, @Valid @RequestBody SaveBuildingBlockRequest request,
                                                 @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.update(id, request, actor));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        service.delete(id, actor);
        return ApiResponse.ok(null);
    }
}
