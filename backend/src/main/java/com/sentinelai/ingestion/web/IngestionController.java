package com.sentinelai.ingestion.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.ingestion.IngestionService.IngestOutcome;
import com.sentinelai.ingestion.dto.BatchIngestRequest;
import com.sentinelai.ingestion.dto.BatchIngestResponse;
import com.sentinelai.ingestion.dto.IngestRequest;
import com.sentinelai.ingestion.dto.IngestResponse;
import com.sentinelai.site.security.ApiKeyPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ANALYST','ADMIN','INGEST')")
@Tag(name = "Ingestion")
public class IngestionController {

    private final IngestionService ingestionService;

    /** (orgId, siteId) resolved from either a user (JWT) or an ingest API key. */
    private record Target(Long orgId, Long siteId) {
    }

    private Target resolve(Object principal) {
        if (principal instanceof ApiKeyPrincipal k) {
            return new Target(k.orgId(), k.siteId());
        }
        if (principal instanceof AppUserPrincipal u) {
            return new Target(u.getOrgId(), null); // UI ingest is untagged by site
        }
        throw new BadRequestException("Unsupported principal");
    }

    /**
     * Ingest one event. When the Kafka pipeline handles it, the event is persisted and enqueued for
     * asynchronous detection, so the response is {@code 202 Accepted} with the event id. On the
     * synchronous path (Kafka disabled or unreachable) detection has already run, so it is {@code 200 OK}.
     */
    @PostMapping("/ingest")
    public ResponseEntity<ApiResponse<IngestResponse>> ingest(@Valid @RequestBody IngestRequest request,
                                                              @AuthenticationPrincipal Object principal) {
        Target t = resolve(principal);
        IngestOutcome outcome = ingestionService.ingest(
                t.orgId(), t.siteId(), request.sourceType(), request.payload(), request.clientEventId());
        HttpStatus status = outcome.dispatch() == IngestionService.Dispatch.KAFKA
                ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(ApiResponse.ok(new IngestResponse(outcome.eventId(), outcome.duplicate())));
    }

    @PostMapping("/ingest/batch")
    public ApiResponse<BatchIngestResponse> ingestBatch(@Valid @RequestBody BatchIngestRequest request,
                                                        @AuthenticationPrincipal Object principal) {
        Target t = resolve(principal);
        if (request.events().size() > ingestionService.maxBatchSize()) {
            throw new BadRequestException("batch exceeds max size of " + ingestionService.maxBatchSize());
        }
        List<Long> ids = new ArrayList<>();
        int duplicates = 0;
        for (IngestRequest e : request.events()) {
            IngestOutcome outcome = ingestionService.ingest(
                    t.orgId(), t.siteId(), e.sourceType(), e.payload(), e.clientEventId());
            ids.add(outcome.eventId());
            if (outcome.duplicate()) {
                duplicates++;
            }
        }
        return ApiResponse.ok(new BatchIngestResponse(ids.size() - duplicates, duplicates, ids));
    }
}
