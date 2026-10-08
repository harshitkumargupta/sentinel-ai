package com.sentinelai.honeytoken;

import com.sentinelai.common.web.RequestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs before security: a request to a decoy URL path (e.g. {@code /api/admin/backup-export}) is
 * recorded as a honeytoken hit and answered with a plain 404, so the caller learns nothing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class HoneypathFilter extends OncePerRequestFilter {

    private final HoneytokenService honeytokens;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (honeytokens.tripPath(request.getRequestURI(), RequestUtils.clientIp(request), request.getHeader("User-Agent"))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }
}
