package com.sentinelai.site.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.common.util.Hashing;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.common.web.RequestUtils;
import com.sentinelai.honeytoken.HoneytokenService;
import com.sentinelai.common.web.ApiResponse.ApiError;
import com.sentinelai.site.domain.ApiKey;
import com.sentinelai.site.domain.SiteStatus;
import com.sentinelai.site.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

/**
 * Authenticates requests carrying an {@code X-API-Key} header (per-site ingest keys). A valid,
 * non-revoked key authenticates as {@code ROLE_INGEST} scoped to its site; an invalid or revoked key
 * is rejected with 401. Requests without the header fall through to JWT authentication.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyRepository apiKeyRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final HoneytokenService honeytokens;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String raw = request.getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        ApiKey key = apiKeyRepository.findWithSiteAndOrgByKeyHash(Hashing.sha256Hex(raw)).orElse(null);
        if (key == null || !key.isActive()) {
            if (key == null) {
                honeytokens.tripApiKey(raw, RequestUtils.clientIp(request), request.getHeader("User-Agent"));
            }
            unauthorized(response, "Invalid or revoked API key");
            return;
        }
        if (key.getSite().getStatus() == SiteStatus.DISABLED) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "LOG_SOURCE_DISABLED", "Log source is disabled");
            return;
        }
        key.setLastUsedAt(clock.instant());
        apiKeyRepository.save(key);

        var principal = new ApiKeyPrincipal(key.getSite().getOrg().getId(), key.getSite().getId());
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + key.getScope())));
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        reject(response, HttpServletResponse.SC_UNAUTHORIZED, "INVALID_API_KEY", message);
    }

    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(ApiError.of(code, message)));
    }
}
