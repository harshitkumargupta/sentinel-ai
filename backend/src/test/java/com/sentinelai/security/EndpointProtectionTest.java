package com.sentinelai.security;

import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Access-control guardrail: enumerates every Spring MVC endpoint and asserts each is EITHER on the
 * documented public allow-list OR rejects an unauthenticated request with 401 (the auth entry point
 * fires before any controller code runs, so probing has no side effects). A newly added endpoint
 * that is unintentionally public — or an addition to the permit-all list — fails this test, and thus
 * the build. This is the automated backstop for the IDOR / broken-access-control sweep.
 */
class EndpointProtectionTest extends IntegrationTestSupport {

    /** Mirrors SecurityConfig.PUBLIC — the only endpoints allowed to be reachable without auth. */
    private static final Set<String> PUBLIC_PATTERNS = Set.of(
            "/api/health",
            "/api/auth/login",
            "/api/auth/refresh",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html");

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("every endpoint is explicitly public or requires authentication (401 without a token)")
    void everyEndpointIsPublicOrAuthenticated() throws Exception {
        List<String> violations = new ArrayList<>();

        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();
            Set<String> patterns = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues()
                    : Set.of();
            HttpMethod method = firstMethod(info);

            for (String pattern : patterns) {
                String url = concreteUrl(pattern);
                boolean expectedPublic = PUBLIC_PATTERNS.stream().anyMatch(p -> matcher.match(p, url));

                int status = mockMvc.perform(request(method, url)).andReturn().getResponse().getStatus();
                boolean actuallyProtected = status == 401;

                if (expectedPublic && actuallyProtected) {
                    violations.add("EXPECTED PUBLIC but got 401: " + method + " " + pattern);
                } else if (!expectedPublic && !actuallyProtected) {
                    violations.add("UNPROTECTED (status " + status + ", expected 401): " + method + " " + pattern
                            + " -> " + handler.getShortLogMessage());
                }
            }
        }

        assertThat(violations)
                .withFailMessage("Endpoint access-control violations:%n%s",
                        String.join("\n", violations))
                .isEmpty();
    }

    private HttpMethod firstMethod(RequestMappingInfo info) {
        return info.getMethodsCondition().getMethods().stream()
                .findFirst()
                .map(m -> HttpMethod.valueOf(m.name()))
                .orElse(HttpMethod.GET);
    }

    /** Replace path variables with a harmless concrete value so the matcher and MockMvc agree. */
    private String concreteUrl(String pattern) {
        return pattern.replaceAll("\\{[^/}]+}", "1");
    }
}
