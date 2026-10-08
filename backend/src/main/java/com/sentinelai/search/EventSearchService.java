package com.sentinelai.search;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.web.PageResponse;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.dto.EventResponse;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.search.query.QueryNode;
import com.sentinelai.search.query.QueryParser;
import com.sentinelai.search.query.QuerySpecification;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Event search (QRadar Log Activity / AQL-lite): structured filters AND a parsed query, always scoped
 * to the caller's org, newest first, paginated, with a bounded CSV export.
 */
@Service
@RequiredArgsConstructor
public class EventSearchService {

    public static final int MAX_PAGE_SIZE = 200;
    public static final int MAX_EXPORT_ROWS = 10_000;

    private final SecurityEventRepository eventRepository;

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> search(AppUserPrincipal actor, SearchFilters filters, String query,
                                              int page, int size) {
        Specification<SecurityEvent> spec = spec(actor, filters, query);
        Page<SecurityEvent> result = eventRepository.findAll(spec, PageRequest.of(Math.max(0, page),
                Math.max(1, Math.min(size, MAX_PAGE_SIZE)), Sort.by(Sort.Direction.DESC, "eventTimestamp", "id")));
        return PageResponse.from(result, EventResponse::from);
    }

    @Transactional(readOnly = true)
    public long count(AppUserPrincipal actor, SearchFilters filters, String query) {
        return eventRepository.count(spec(actor, filters, query));
    }

    @Transactional(readOnly = true)
    public String exportCsv(AppUserPrincipal actor, SearchFilters filters, String query) {
        List<SecurityEvent> rows = eventRepository.findAll(spec(actor, filters, query),
                PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(Sort.Direction.DESC, "eventTimestamp", "id"))).getContent();
        return CsvWriter.events(rows);
    }

    Specification<SecurityEvent> spec(AppUserPrincipal actor, SearchFilters f, String query) {
        QueryNode ast = QueryParser.parse(query);
        if (f.from() != null && f.to() != null && f.from().isAfter(f.to())) {
            throw new BadRequestException("'from' must be before 'to'");
        }
        Specification<SecurityEvent> filters = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("org").get("id"), actor.getOrgId()));
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("eventTimestamp"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("eventTimestamp"), f.to()));
            }
            if (f.sourceId() != null) {
                p.add(cb.equal(root.get("site").get("id"), f.sourceId()));
            }
            if (f.ip() != null && !f.ip().isBlank()) {
                p.add(cb.equal(root.get("sourceIp"), f.ip().trim()));
            }
            if (f.user() != null && !f.user().isBlank()) {
                p.add(cb.equal(root.get("username"), f.user().trim()));
            }
            if (f.eventType() != null) {
                p.add(cb.equal(root.get("eventType"), f.eventType()));
            }
            if (f.outcome() != null) {
                p.add(cb.equal(root.get("outcome"), f.outcome()));
            }
            if (f.severity() != null) {
                p.add(cb.equal(root.get("severity"), f.severity()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return filters.and(QuerySpecification.of(ast));
    }
}
