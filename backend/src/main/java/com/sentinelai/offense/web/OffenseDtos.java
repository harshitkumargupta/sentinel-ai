package com.sentinelai.offense.web;

import com.sentinelai.event.dto.EventResponse;
import com.sentinelai.offense.Magnitude;
import com.sentinelai.offense.ThreatIntelSignal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Offense API DTOs (QRadar naming over SentinelAI incidents). */
public final class OffenseDtos {

    private OffenseDtos() {
    }

    public record OffenseSummary(Long id, String title, String status, String severity, Integer riskScore,
                                 Magnitude magnitude, String offenseSource, List<String> categories,
                                 int eventCount, int alertCount, List<String> logSources, String assignedTo,
                                 Long assignedToId, String feedback, long notes, Instant firstSeen,
                                 Instant lastSeen, Instant createdAt) {
    }

    public record OffenseDetail(OffenseSummary offense, List<ThreatIntelSignal.Match> threatIntel,
                                List<EventResponse> events, List<NoteView> notes, List<AffectedAsset> assets) {
    }

    /** An inventoried asset touched by the offense, with its open vulnerabilities. */
    public record AffectedAsset(Long id, String label, String criticality, String type, String environment,
                                String owner, List<com.sentinelai.vuln.VulnerabilityService.VulnView> vulnerabilities) {
    }

    public record OffensePage(List<OffenseSummary> content, int page, int size, long totalElements, int totalPages) {
    }

    public record NoteView(Long id, String author, Long authorId, String body, Instant createdAt,
                           Instant updatedAt, String editedBy) {
    }

    public record AddNoteRequest(@NotBlank @Size(max = 2000) String body) {
    }

    public record Assignee(Long id, String username, String role) {
    }
}
