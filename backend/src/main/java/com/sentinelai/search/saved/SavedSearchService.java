package com.sentinelai.search.saved;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.ConflictException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.event.domain.EventOutcome;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.search.EventSearchService;
import com.sentinelai.search.SearchFilters;
import com.sentinelai.search.query.QueryParser;
import com.sentinelai.search.query.QuerySpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Per-user saved searches. The query is validated when saved, filters are a fixed set (time range
 * keyword, log source, IP, user, event type, outcome, severity), and a pinned search's widget data
 * is its count over its range plus an hourly trend for the last 24 hours.
 */
@Service
@RequiredArgsConstructor
public class SavedSearchService {

    static final int MAX_PER_USER = 50;
    static final int TREND_HOURS = 24;
    private static final Map<String, Duration> RANGES = Map.of(
            "1h", Duration.ofHours(1), "24h", Duration.ofHours(24), "7d", Duration.ofDays(7), "30d", Duration.ofDays(30));

    private final SavedSearchRepository repository;
    private final EventSearchService search;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** Saved filters; {@code range} is 1h/24h/7d/30d/all (relative to when the search runs). */
    public record Filters(String range, Long sourceId, String ip, String user, EventType eventType,
                          EventOutcome outcome, Severity severity) {
    }

    public record View(Long id, String name, String query, Filters filters, boolean pinned, Instant updatedAt) {
    }

    public record Stats(Long id, String name, long count, List<Long> hourly, Instant from, Instant to) {
    }

    @Transactional(readOnly = true)
    public List<View> list(AppUserPrincipal actor) {
        return repository.findByOwnerIdOrderByNameAsc(actor.getUserId()).stream().map(this::view).toList();
    }

    @Transactional
    public View save(Long id, String name, String query, Filters filters, boolean pinned, AppUserPrincipal actor) {
        String cleanName = name.trim();
        String q = query == null || query.isBlank() ? null : query.trim();
        QuerySpecification.of(QueryParser.parse(q)); // 400 now, not when the widget renders
        if (filters != null && filters.range() != null && !"all".equals(filters.range()) && !RANGES.containsKey(filters.range())) {
            throw new BadRequestException("range must be one of 1h, 24h, 7d, 30d, all");
        }
        SavedSearch s = id == null ? new SavedSearch() : load(id, actor);
        if (id == null && repository.countByOwnerId(actor.getUserId()) >= MAX_PER_USER) {
            throw new BadRequestException("You can save at most " + MAX_PER_USER + " searches");
        }
        if ((id == null || !cleanName.equals(s.getName())) && repository.existsByOwnerIdAndName(actor.getUserId(), cleanName)) {
            throw new ConflictException("You already have a saved search with that name");
        }
        s.setOrgId(actor.getOrgId());
        s.setOwnerId(actor.getUserId());
        s.setName(cleanName);
        s.setQuery(q);
        s.setFilters(write(filters));
        s.setPinned(pinned);
        return view(repository.save(s));
    }

    @Transactional
    public void delete(Long id, AppUserPrincipal actor) {
        repository.delete(load(id, actor));
    }

    @Transactional(readOnly = true)
    public List<Stats> pinnedStats(AppUserPrincipal actor) {
        return repository.findByOwnerIdAndPinnedTrueOrderByNameAsc(actor.getUserId()).stream()
                .map(s -> stats(s, actor)).toList();
    }

    @Transactional(readOnly = true)
    public Stats stats(Long id, AppUserPrincipal actor) {
        return stats(load(id, actor), actor);
    }

    private Stats stats(SavedSearch s, AppUserPrincipal actor) {
        Filters f = read(s.getFilters());
        Instant now = clock.instant();
        Duration range = f == null || f.range() == null ? null : RANGES.get(f.range());
        Instant from = range == null ? null : now.minus(range);
        long count = search.count(actor, filters(f, from, now), s.getQuery());
        Instant hour = now.truncatedTo(ChronoUnit.HOURS);
        List<Long> trend = new ArrayList<>(TREND_HOURS);
        for (int h = TREND_HOURS - 1; h >= 0; h--) {
            Instant start = hour.minus(h, ChronoUnit.HOURS);
            Instant end = h == 0 ? now : start.plus(1, ChronoUnit.HOURS).minusNanos(1);
            trend.add(search.count(actor, filters(f, start, end), s.getQuery()));
        }
        return new Stats(s.getId(), s.getName(), count, trend, from, now);
    }

    private static SearchFilters filters(Filters f, Instant from, Instant to) {
        if (f == null) {
            return new SearchFilters(from, to, null, null, null, null, null, null);
        }
        return new SearchFilters(from, to, f.sourceId(), f.ip(), f.user(), f.eventType(), f.outcome(), f.severity());
    }

    private View view(SavedSearch s) {
        return new View(s.getId(), s.getName(), s.getQuery(), read(s.getFilters()), s.isPinned(), s.getUpdatedAt());
    }

    private SavedSearch load(Long id, AppUserPrincipal actor) {
        return repository.findById(id).filter(s -> s.getOwnerId().equals(actor.getUserId()))
                .orElseThrow(() -> new NotFoundException("Saved search not found: " + id));
    }

    private String write(Filters f) {
        try {
            return f == null ? null : objectMapper.writeValueAsString(f);
        } catch (Exception e) {
            throw new BadRequestException("Invalid filters");
        }
    }

    private Filters read(String json) {
        try {
            return json == null ? null : objectMapper.readValue(json, Filters.class);
        } catch (Exception e) {
            return null;
        }
    }
}
