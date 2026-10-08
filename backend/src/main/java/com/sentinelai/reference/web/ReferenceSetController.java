package com.sentinelai.reference.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.common.web.PageResponse;
import com.sentinelai.reference.ReferenceSetService;
import com.sentinelai.reference.web.ReferenceSetDtos.AddItemsRequest;
import com.sentinelai.reference.web.ReferenceSetDtos.AddItemsResult;
import com.sentinelai.reference.web.ReferenceSetDtos.CreateSetRequest;
import com.sentinelai.reference.web.ReferenceSetDtos.ItemView;
import com.sentinelai.reference.web.ReferenceSetDtos.SetView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Reference sets: read (any role), add/remove values (analyst+), create/delete sets (admin). */
@RestController
@RequestMapping("/api/reference-sets")
@RequiredArgsConstructor
@Tag(name = "Reference sets")
public class ReferenceSetController {

    private final ReferenceSetService service;

    @GetMapping
    public ApiResponse<List<SetView>> list(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.list(actor));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SetView> create(@Valid @RequestBody CreateSetRequest request,
                                       @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.create(request, actor));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        service.delete(id, actor);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/items")
    public ApiResponse<PageResponse<ItemView>> items(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "50") int size,
                                                     @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.items(id, page, size, actor));
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<AddItemsResult> add(@PathVariable Long id, @Valid @RequestBody AddItemsRequest request,
                                           @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.addItems(id, request.values(), request.note(), actor));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<Void> remove(@PathVariable Long id, @PathVariable Long itemId,
                                    @AuthenticationPrincipal AppUserPrincipal actor) {
        service.removeItem(id, itemId, actor);
        return ApiResponse.ok(null);
    }
}
