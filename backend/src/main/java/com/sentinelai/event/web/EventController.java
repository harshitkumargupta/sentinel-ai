package com.sentinelai.event.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.common.web.PageResponse;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.dto.CreateEventRequest;
import com.sentinelai.event.dto.EventResponse;
import com.sentinelai.event.service.EventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Events")
public class EventController {

    private final EventService eventService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<EventResponse> create(@Valid @RequestBody CreateEventRequest request,
                                             @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(eventService.create(request, actor));
    }

    @GetMapping
    public ApiResponse<PageResponse<EventResponse>> list(
            @AuthenticationPrincipal AppUserPrincipal actor,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) EventType type,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "eventTimestamp,desc") String sort) {

        var pageable = PageRequest.of(page, Math.min(size, 200), parseSort(sort));
        var result = eventService.list(actor, ip, user, type, severity, from, to, pageable);
        return ApiResponse.ok(PageResponse.from(result, e -> e));
    }

    @GetMapping("/{id}")
    public ApiResponse<EventResponse> get(@PathVariable Long id,
                                          @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(eventService.get(id, actor));
    }

    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String field = parts[0].isBlank() ? "eventTimestamp" : parts[0];
        Sort.Direction dir = parts.length > 1 && parts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, field);
    }
}
