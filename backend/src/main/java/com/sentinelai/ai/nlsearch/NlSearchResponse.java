package com.sentinelai.ai.nlsearch;

import com.sentinelai.event.dto.EventResponse;

import java.util.List;
import java.util.Map;

/**
 * NL-search result: the interpreted filter (for UI chips so the user sees what ran) plus the matching
 * events from the parameterized query.
 */
public record NlSearchResponse(Map<String, String> interpretedFilter, List<EventResponse> results,
                               long total) {
}
