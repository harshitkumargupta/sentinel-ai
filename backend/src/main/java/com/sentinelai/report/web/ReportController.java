package com.sentinelai.report.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.report.ReportScheduleService;
import com.sentinelai.report.ReportService;
import com.sentinelai.report.ReportSummary;
import com.sentinelai.report.ReportType;
import com.sentinelai.report.web.ReportDtos.GenerateRequest;
import com.sentinelai.report.web.ReportDtos.SaveScheduleRequest;
import com.sentinelai.report.web.ReportDtos.ScheduleView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

/** Reports: generate + list + download (analyst+), schedules (admin), delete (admin). */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reports;
    private final ReportScheduleService schedules;

    public record TypeView(String id, String title) {
    }

    @GetMapping("/types")
    public ApiResponse<List<TypeView>> types() {
        return ApiResponse.ok(Arrays.stream(ReportType.values()).map(t -> new TypeView(t.name(), t.title())).toList());
    }

    @PostMapping("/generate")
    public ApiResponse<ReportSummary> generate(@Valid @RequestBody GenerateRequest req,
                                               @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(reports.generate(actor.getOrgId(), actor.getUserId(), req.type(), req.format(),
                req.from(), req.to(), null));
    }

    @GetMapping
    public ApiResponse<List<ReportSummary>> list(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(reports.list(actor.getOrgId()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        ReportService.Download d = reports.download(actor.getOrgId(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.filename()).build().toString())
                .contentType(MediaType.parseMediaType(d.mediaType()))
                .body(d.content());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        reports.delete(actor.getOrgId(), actor.getUserId(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/schedules")
    public ApiResponse<List<ScheduleView>> schedules(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(schedules.list(actor.getOrgId()).stream().map(ScheduleView::from).toList());
    }

    @PostMapping("/schedules")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ScheduleView> create(@Valid @RequestBody SaveScheduleRequest req,
                                            @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(ScheduleView.from(schedules.save(actor.getOrgId(), actor.getUserId(), null, req)));
    }

    @PutMapping("/schedules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ScheduleView> update(@PathVariable Long id, @Valid @RequestBody SaveScheduleRequest req,
                                            @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(ScheduleView.from(schedules.save(actor.getOrgId(), actor.getUserId(), id, req)));
    }

    @DeleteMapping("/schedules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteSchedule(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        schedules.delete(actor.getOrgId(), actor.getUserId(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/schedules/{id}/run")
    public ApiResponse<ReportSummary> runNow(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(schedules.runNow(actor.getOrgId(), actor.getUserId(), id));
    }
}
