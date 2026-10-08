package com.sentinelai.offense.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.offense.Magnitude;
import com.sentinelai.offense.OffenseService;
import com.sentinelai.offense.web.OffenseDtos.AddNoteRequest;
import com.sentinelai.offense.web.OffenseDtos.Assignee;
import com.sentinelai.offense.web.OffenseDtos.NoteView;
import com.sentinelai.offense.web.OffenseDtos.OffenseDetail;
import com.sentinelai.offense.web.OffenseDtos.OffensePage;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Offenses (QRadar naming) over incidents: magnitude-ranked list, detail with linked events and
 * threat-intel hits, notes. Status, assignment and feedback stay on {@code /api/incidents/{id}/...}.
 */
@RestController
@RequestMapping("/api/offenses")
@RequiredArgsConstructor
@Tag(name = "Offenses")
public class OffenseController {

    private final OffenseService service;

    @GetMapping
    public ApiResponse<OffensePage> list(@AuthenticationPrincipal AppUserPrincipal actor,
                                         @RequestParam(required = false) IncidentStatus status,
                                         @RequestParam(required = false) Long assigneeId,
                                         @RequestParam(required = false) Integer minMagnitude,
                                         @RequestParam(defaultValue = "MAGNITUDE") OffenseService.SortBy sort,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(actor, status, assigneeId, minMagnitude, sort, page, size));
    }

    @GetMapping("/assignees")
    public ApiResponse<List<Assignee>> assignees(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.assignees(actor));
    }

    @GetMapping("/{id}")
    public ApiResponse<OffenseDetail> get(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.get(id, actor));
    }

    @GetMapping("/{id}/magnitude")
    public ApiResponse<Magnitude> magnitude(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.magnitude(id, actor));
    }

    @GetMapping("/{id}/notes")
    public ApiResponse<List<NoteView>> notes(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.notes(id, actor));
    }

    @PutMapping("/{id}/notes/{noteId}")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<NoteView> editNote(@PathVariable Long id, @PathVariable Long noteId,
                                          @Valid @RequestBody AddNoteRequest request,
                                          @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.editNote(id, noteId, request.body(), actor));
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<Void> deleteNote(@PathVariable Long id, @PathVariable Long noteId,
                                        @AuthenticationPrincipal AppUserPrincipal actor) {
        service.deleteNote(id, noteId, actor);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ApiResponse<NoteView> addNote(@PathVariable Long id, @Valid @RequestBody AddNoteRequest request,
                                         @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(service.addNote(id, request.body(), actor));
    }
}
