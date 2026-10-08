package com.sentinelai.ingestion.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.detection.engine.RunContextHolder;
import com.sentinelai.ingestion.IngestionProperties;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.ingestion.IngestionService.IngestOutcome;
import com.sentinelai.site.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Raw log lines → parser → existing normalizer → pipeline, for the agent, file uploads and sample
 * replay. Parse errors and rejected records are counted on the log source. A replay re-times the
 * batch so its newest record is "now" and tags it (client ids / alert run id) so the demo reset can
 * find it; uploads and agent lines are real data and are never tagged.
 */
@Service
@RequiredArgsConstructor
public class LogIngestService {

    private static final List<String> TIME_FIELDS = List.of("timestamp", "eventTimestamp");

    private final LogParserService parserService;
    private final IngestionService ingestionService;
    private final IngestionProperties ingestionProperties;
    private final SiteRepository siteRepository;
    private final Clock clock;

    /** {@code replayTag}: null for real data; else a run id used to tag (and re-time) a replay. */
    public record Request(Long orgId, Long siteId, LogFormat format, List<String> lines, String replayTag) {
    }

    public record Report(LogFormat format, int lines, int parsed, int accepted, int duplicates, int skipped,
                         int parseErrors, int rejected, List<String> errorSamples) {
    }

    public Report ingest(Request req) {
        if (req.lines() == null || req.lines().isEmpty()) {
            throw new BadRequestException("No lines to ingest");
        }
        if (req.lines().size() > ingestionProperties.getMaxLinesPerUpload()) {
            throw new BadRequestException("Too many lines (max " + ingestionProperties.getMaxLinesPerUpload() + ")");
        }
        ParseResult parsed = parserService.parse(req.format(), req.lines(), clock.instant());
        Duration shift = req.replayTag() == null ? Duration.ZERO : shiftToNow(parsed.records());

        int accepted = 0;
        int duplicates = 0;
        int rejected = 0;
        List<String> samples = new ArrayList<>(parsed.errorSamples());
        if (req.replayTag() != null) {
            RunContextHolder.set("replay-" + req.replayTag());
        }
        try {
            int i = 0;
            for (ParsedRecord rec : parsed.records()) {
                ObjectNode payload = rec.payload();
                if (!shift.isZero()) {
                    retime(payload, shift);
                }
                String clientId = req.replayTag() == null ? null : "replay:" + req.replayTag() + ":" + i;
                i++;
                try {
                    IngestOutcome out = ingestionService.ingest(req.orgId(), req.siteId(), rec.sourceType(), payload, clientId);
                    if (out.duplicate()) {
                        duplicates++;
                    } else {
                        accepted++;
                    }
                } catch (BadRequestException e) {
                    rejected++;
                    if (samples.size() < 10) {
                        samples.add("record " + i + ": " + e.getMessage());
                    }
                }
            }
        } finally {
            if (req.replayTag() != null) {
                RunContextHolder.clear();
            }
        }
        long failures = (long) parsed.errors() + rejected;
        if (req.siteId() != null && failures > 0) {
            siteRepository.addParseErrors(req.siteId(), failures);
        }
        return new Report(req.format(), req.lines().size(), parsed.records().size(), accepted, duplicates,
                parsed.skipped(), parsed.errors(), rejected, samples);
    }

    private Duration shiftToNow(List<ParsedRecord> records) {
        Instant newest = records.stream().map(r -> time(r.payload())).filter(Objects::nonNull)
                .max(Instant::compareTo).orElse(null);
        return newest == null ? Duration.ZERO : Duration.between(newest, clock.instant());
    }

    private static void retime(ObjectNode payload, Duration shift) {
        for (String f : TIME_FIELDS) {
            Instant t = parse(payload.get(f));
            if (t != null) {
                payload.put(f, t.plus(shift).toString());
            }
        }
    }

    private static Instant time(ObjectNode payload) {
        for (String f : TIME_FIELDS) {
            Instant t = parse(payload.get(f));
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private static Instant parse(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        try {
            return Instant.parse(node.asText());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
