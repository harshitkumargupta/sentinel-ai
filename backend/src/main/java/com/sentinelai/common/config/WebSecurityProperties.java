package com.sentinelai.common.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * HTTP security hardening config: the CORS allow-list and the response security headers. All values
 * are overridable per profile and via env var; HSTS is off by default and switched on only in the
 * {@code prod} profile. A wildcard origin combined with credentials is rejected at startup.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.security.web")
public class WebSecurityProperties {

    private final Cors cors = new Cors();
    private final Headers headers = new Headers();

    /** Emit {@code Strict-Transport-Security}. Off in dev (plain HTTP); on in prod. */
    private boolean hstsEnabled = false;
    /** HSTS max-age in seconds (default 1 year). */
    private long hstsMaxAgeSeconds = 31_536_000L;

    @Getter
    @Setter
    public static class Cors {
        /** Exact allowed origins — no wildcard when credentials are allowed. */
        private List<String> allowedOrigins = List.of("http://localhost:5173");
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        private List<String> allowedHeaders = List.of("Authorization", "Content-Type", "X-API-Key", "X-Requested-With");
        private List<String> exposedHeaders = List.of("X-Trace-Id");
        private boolean allowCredentials = true;
        private long maxAgeSeconds = 3600;

        @AssertTrue(message = "CORS must not allow a wildcard origin ('*') together with credentials")
        public boolean isOriginsSafeWithCredentials() {
            return !allowCredentials || allowedOrigins.stream().noneMatch(o -> o.contains("*"));
        }
    }

    @Getter
    @Setter
    public static class Headers {
        /** Content-Security-Policy. The API serves JSON only, so the default policy is restrictive. */
        @NotBlank
        private String contentSecurityPolicy =
                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
        @NotBlank
        private String referrerPolicy = "no-referrer";
        @NotBlank
        private String permissionsPolicy = "geolocation=(), microphone=(), camera=(), payment=()";
        /** Sent as X-Frame-Options; CSP frame-ancestors above is the modern equivalent. */
        @NotBlank
        private String frameOptions = "DENY";
    }
}
