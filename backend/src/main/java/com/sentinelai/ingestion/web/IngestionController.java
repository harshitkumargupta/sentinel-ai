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
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
@Tag(name = "Ingestion")
public class IngestionController {

    private final IngestionService ingestionService;

    @PostMapping("/ingest")
    public ApiResponse<IngestResponse> ingest(@Valid @RequestBody IngestRequest request,
                                              @AuthenticationPrincipal AppUserPrincipal actor) {
        IngestOutcome outcome = ingestionService.ingest(
                actor.getOrgId(), request.sourceType(), request.payload(), request.clientEventId());
        return ApiResponse.ok(new IngestResponse(outcome.eventId(), outcome.duplicate()));
    }

    @PostMapping("/ingest/batch")
    public ApiResponse<BatchIngestResponse> ingestBatch(@Valid @RequestBody BatchIngestRequest request,
                                                        @AuthenticationPrincipal AppUserPrincipal actor) {
        if (request.events().size() > ingestionService.maxBatchSize()) {
            throw new BadRequestException("batch exceeds max size of " + ingestionService.maxBatchSize());
        }
        List<Long> ids = new ArrayList<>();
        int duplicates = 0;
        for (IngestRequest e : request.events()) {
            IngestOutcome outcome = ingestionService.ingest(
                    actor.getOrgId(), e.sourceType(), e.payload(), e.clientEventId());
            ids.add(outcome.eventId());
            if (outcome.duplicate()) {
                duplicates++;
            }
        }
        return ApiResponse.ok(new BatchIngestResponse(ids.size() - duplicates, duplicates, ids));
    }
}
