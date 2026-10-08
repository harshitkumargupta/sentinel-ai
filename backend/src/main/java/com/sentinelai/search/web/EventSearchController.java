package com.sentinelai.search.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.common.web.PageResponse;
import com.sentinelai.event.domain.EventOutcome;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.dto.EventResponse;
import com.sentinelai.search.EventSearchService;
import com.sentinelai.search.SearchFilters;
import com.sentinelai.search.nl.PlainEnglishTranslator;
import com.sentinelai.search.query.QueryField;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** Event search with structured filters + query language; CSV export. Any authenticated role. */
@RestController
@RequestMapping("/api/search/events")
@RequiredArgsConstructor
@Tag(name = "Event search")
public class EventSearchController {

    private final EventSearchService service;
    private final PlainEnglishTranslator translator;

    public record FieldHelp(String name, String kind, List<String> values) {
    }

    @GetMapping
    public ApiResponse<PageResponse<EventResponse>> search(
            @AuthenticationPrincipal AppUserPrincipal actor,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) EventOutcome outcome,
            @RequestParam(required = false) Severity severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(service.search(actor,
                new SearchFilters(from, to, sourceId, ip, user, eventType, outcome, severity), q, page, size));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal AppUserPrincipal actor,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) EventOutcome outcome,
            @RequestParam(required = false) Severity severity) {
        String csv = service.exportCsv(actor,
                new SearchFilters(from, to, sourceId, ip, user, eventType, outcome, severity), q);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sentinel-events.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    /** Plain English → query (offline, rule-based). Never runs the search itself. */
    @GetMapping("/translate")
    public ApiResponse<PlainEnglishTranslator.Translation> translate(@RequestParam String text) {
        if (text.length() > 300) {
            throw new BadRequestException("Keep the question under 300 characters");
        }
        return ApiResponse.ok(translator.translate(text));
    }

    @GetMapping("/top")
    public ApiResponse<List<EventSearchService.TopRow>> top(@AuthenticationPrincipal AppUserPrincipal actor,
                                                            @RequestParam(required = false) String q,
                                                            @RequestParam String field,
                                                            @RequestParam(defaultValue = "10") int n) {
        return ApiResponse.ok(service.top(actor, new SearchFilters(null, null, null, null, null, null, null, null), q, field, n));
    }

    /** Field names, kinds and allowed values, for the search page's query help. */
    @GetMapping("/fields")
    public ApiResponse<List<FieldHelp>> fields() {
        return ApiResponse.ok(Arrays.stream(QueryField.values())
                .map(f -> new FieldHelp(f.displayName(), f.kind().name(), f.allowedValues())).toList());
    }
}
