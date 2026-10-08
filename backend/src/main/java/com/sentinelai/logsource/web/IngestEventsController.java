package com.sentinelai.logsource.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.ingestion.IngestionService.IngestOutcome;
import com.sentinelai.logsource.LogSourceService;
import com.sentinelai.site.security.ApiKeyPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Log-source ingest: {@code POST /api/ingest/events} with the source's {@code X-API-Key}. Accepts one
 * event object, a JSON array, or {@code {"events":[...]}}; each element is either a payload or
 * {@code {"payload":{...}}}. Rate limited per key (RateLimitFilter), capped per request and per
 * event, tagged with the key's source, and fed through the normal pipeline. Bad elements are
 * rejected individually (counted as parse errors on the source) without failing the batch.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('INGEST')")
@Tag(name = "Ingestion")
public class IngestEventsController {

    private static final int MAX_ERRORS_REPORTED = 20;

    private final IngestionService ingestionService;
    private final LogSourceService logSourceService;

    public record RejectedEvent(int index, String reason) {
    }

    public record IngestEventsResponse(int accepted, int duplicates, int rejected, List<Long> eventIds,
                                       List<RejectedEvent> errors) {
    }

    @PostMapping("/api/ingest/events")
    public ApiResponse<IngestEventsResponse> ingest(@RequestBody JsonNode body,
                                                    @AuthenticationPrincipal ApiKeyPrincipal source) {
        List<JsonNode> events = elements(body);
        if (events.isEmpty()) {
            throw new BadRequestException("No events in request");
        }
        if (events.size() > ingestionService.maxBatchSize()) {
            throw new BadRequestException("Too many events in one request (max " + ingestionService.maxBatchSize() + ")");
        }
        int accepted = 0;
        int duplicates = 0;
        List<Long> ids = new ArrayList<>();
        List<RejectedEvent> errors = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            JsonNode e = events.get(i);
            JsonNode payload = e.has("payload") ? e.get("payload") : e;
            if (!payload.isObject()) {
                errors.add(new RejectedEvent(i, "event must be a JSON object"));
                continue;
            }
            try {
                IngestOutcome outcome = ingestionService.ingest(source.orgId(), source.siteId(), "generic", payload, null);
                ids.add(outcome.eventId());
                if (outcome.duplicate()) {
                    duplicates++;
                } else {
                    accepted++;
                }
            } catch (BadRequestException ex) {
                errors.add(new RejectedEvent(i, ex.getMessage()));
            }
        }
        logSourceService.recordParseErrors(source.siteId(), errors.size());
        return ApiResponse.ok(new IngestEventsResponse(accepted, duplicates, errors.size(), ids,
                errors.stream().limit(MAX_ERRORS_REPORTED).toList()));
    }

    private static List<JsonNode> elements(JsonNode body) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode list = body != null && body.isObject() && body.has("events") ? body.get("events") : body;
        if (list == null || list.isNull()) {
            return out;
        }
        if (list.isArray()) {
            list.forEach(out::add);
        } else {
            out.add(list);
        }
        return out;
    }
}
