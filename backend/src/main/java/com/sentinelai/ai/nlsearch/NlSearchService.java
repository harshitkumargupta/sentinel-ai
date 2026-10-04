package com.sentinelai.ai.nlsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.ai.AiProperties;
import com.sentinelai.ai.llm.LlmClient;
import com.sentinelai.ai.llm.LlmRequest;
import com.sentinelai.ai.llm.LlmUnavailableException;
import com.sentinelai.ai.prompt.PromptTemplates;
import com.sentinelai.ai.security.PromptSanitizer;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.dto.EventResponse;
import com.sentinelai.event.service.EventService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Safe natural-language event search. The LLM only ever produces a structured, allow-listed filter
 * (never SQL or raw expressions); the filter is validated and capped, then run through the existing
 * parameterized event query. The interpreted filter is returned so the UI can show what actually ran.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NlSearchService {

    private static final Pattern IPV4 =
            Pattern.compile("^(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)$");
    private static final Pattern SQL_LIKE = Pattern.compile(
            "(?i)(\\b(select|insert|update|delete|drop|union|exec|sleep)\\b|--|;|/\\*|\\bor\\s+1\\s*=\\s*1\\b)");
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z0-9._@-]{1,100}$");
    private static final Pattern CC = Pattern.compile("^[A-Za-z]{2}$");

    private final AiProperties props;
    private final LlmClient llm;
    private final PromptTemplates templates;
    private final PromptSanitizer sanitizer;
    private final EventService eventService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;
    private final Clock clock;

    public NlSearchResponse search(String query, AppUserPrincipal actor) {
        if (query == null || query.isBlank()) {
            throw new BadRequestException("query is required");
        }
        if (query.length() > 500) {
            throw new BadRequestException("query too long");
        }
        if (SQL_LIKE.matcher(query).find()) {
            meters.counter("sentinel.ai.nlsearch", "outcome", "rejected_sqlish").increment();
            throw new BadRequestException("Query not understood. Use plain language, not SQL.");
        }
        if (!props.isEnabled()) {
            throw new BadRequestException("Natural-language search is unavailable (AI disabled).");
        }

        JsonNode filterJson = callModel(query, actor.getOrgId());
        NlFilter filter = validate(filterJson);

        var page = eventService.list(actor, filter.ip(), filter.user(), filter.type(),
                filter.severity(), filter.from(), filter.to(), PageRequest.of(0, filter.limit()));
        List<EventResponse> results = page.getContent();
        meters.counter("sentinel.ai.nlsearch", "outcome", "ok").increment();
        return new NlSearchResponse(toChips(filter), results, page.getTotalElements());
    }

    private JsonNode callModel(String query, Long orgId) {
        try {
            String system = templates.load("nlsearch.system");
            String data = sanitizer.wrapData(sanitizer.sanitizeField(query, 500));
            String user = templates.render("nlsearch.user", Map.of("data", data));
            var resp = llm.complete(new LlmRequest(orgId, "nl-search", system, user, 300));
            String content = resp.content();
            int s = content.indexOf('{');
            int e = content.lastIndexOf('}');
            if (s < 0 || e <= s) {
                throw new BadRequestException("Could not interpret the search.");
            }
            return objectMapper.readTree(content.substring(s, e + 1));
        } catch (LlmUnavailableException ex) {
            meters.counter("sentinel.ai.nlsearch", "outcome", "unavailable").increment();
            throw new BadRequestException("Search is temporarily unavailable; please try again.");
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Could not interpret the search.");
        }
    }

    private NlFilter validate(JsonNode root) {
        JsonNode f = root.path("filters");
        String ip = text(f, "ip");
        if (ip != null && !IPV4.matcher(ip).matches()) {
            throw new BadRequestException("Invalid IP in interpreted filter.");
        }
        String user = text(f, "user");
        if (user != null && !IDENT.matcher(user).matches()) {
            throw new BadRequestException("Invalid user in interpreted filter.");
        }
        EventType type = enumValue(EventType.class, text(f, "type"));
        Severity severity = enumValue(Severity.class, text(f, "severity"));
        String country = text(f, "country");
        if (country != null && !CC.matcher(country).matches()) {
            throw new BadRequestException("Invalid country in interpreted filter.");
        }

        Instant from = null;
        Instant to = null;
        if (root.hasNonNull("rangeDays")) {
            int days = Math.min(root.path("rangeDays").asInt(), props.getNlSearch().getMaxRangeDays());
            if (days > 0) {
                to = clock.instant();
                from = to.minus(days, ChronoUnit.DAYS);
            }
        }
        int limit = root.hasNonNull("limit")
                ? Math.min(Math.max(1, root.path("limit").asInt()), props.getNlSearch().getMaxLimit())
                : Math.min(50, props.getNlSearch().getMaxLimit());

        // country is applied as a chip for transparency but not a server filter (events query has no
        // country predicate); surface it so the user sees it was understood.
        return new NlFilter(ip, user, type, severity, country, from, to, limit);
    }

    private Map<String, String> toChips(NlFilter f) {
        Map<String, String> chips = new LinkedHashMap<>();
        if (f.ip() != null) chips.put("ip", f.ip());
        if (f.user() != null) chips.put("user", f.user());
        if (f.type() != null) chips.put("type", f.type().name());
        if (f.severity() != null) chips.put("severity", f.severity().name());
        if (f.country() != null) chips.put("country", f.country());
        if (f.from() != null) chips.put("from", f.from().toString());
        if (f.to() != null) chips.put("to", f.to().toString());
        chips.put("limit", String.valueOf(f.limit()));
        return chips;
    }

    private static String text(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unsupported filter value: " + value);
        }
    }
}
