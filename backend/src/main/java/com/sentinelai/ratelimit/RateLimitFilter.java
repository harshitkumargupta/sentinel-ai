package com.sentinelai.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.util.Hashing;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.common.web.ApiResponse.ApiError;
import com.sentinelai.common.web.RequestUtils;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.service.SecurityEventRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Applies per-group token-bucket limits to sensitive endpoints (login strict, ingest generous),
 * keyed by API key / IP. On breach: 429 in the unified error format with Retry-After + X-RateLimit-*
 * headers, and a self-monitored API_ABUSE event. Runs before auth so unauthenticated floods are capped.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Long DEFAULT_ORG = 1L;

    private final RateLimiter rateLimiter;
    private final RateLimitProperties props;
    private final SecurityEventRecorder eventRecorder;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (!props.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI(); // robust across servlet + MockMvc
        String ip = RequestUtils.clientIp(request);
        RateLimitProperties.Bucket bucket;
        String key;

        if (path.equals("/api/auth/login")) {
            bucket = props.getLogin();
            key = "login:ip:" + ip;
        } else if (path.startsWith("/api/events/ingest")) {
            bucket = props.getIngest();
            String apiKey = request.getHeader("X-API-Key");
            key = apiKey != null ? "ingest:key:" + Hashing.sha256Hex(apiKey) : "ingest:ip:" + ip;
        } else {
            chain.doFilter(request, response);
            return;
        }

        RateLimiter.Result r = rateLimiter.tryAcquire(key, bucket.getCapacity(), bucket.getRefillPerSecond());
        response.setHeader("X-RateLimit-Limit", String.valueOf(r.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, r.remaining())));
        if (r.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(r.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(ApiError.of("RATE_LIMITED", "Too many requests; retry after "
                        + r.retryAfterSeconds() + "s")));
        safeRecord(ip, path);
    }

    private void safeRecord(String ip, String path) {
        try {
            eventRecorder.record(DEFAULT_ORG, EventType.API_ABUSE, Severity.MEDIUM, null, ip, path,
                    "{\"reason\":\"RATE_LIMITED\"}");
        } catch (Exception ignored) {
            // never fail the response because of self-monitoring
        }
    }
}
