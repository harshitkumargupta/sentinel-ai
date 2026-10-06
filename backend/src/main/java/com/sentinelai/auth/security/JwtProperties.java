package com.sentinelai.auth.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT configuration. The HMAC secret comes only from env ({@code JWT_SECRET}); a dev-only default
 * lives in {@code application-dev.yml}. Access tokens are signed HS256 and carry a pinned issuer and
 * audience that are required at verification time, with a small configurable clock skew.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.jwt")
public class JwtProperties {

    /** HMAC-SHA256 secret; must be >= min-secret-length bytes. Blank is rejected at startup. */
    private String secret = "";

    @Min(1_000)
    private long accessTokenTtl = 900_000L;     // 15 minutes (ms)

    @Min(60_000)
    private long refreshTokenTtl = 604_800_000L; // 7 days (ms)

    @NotBlank
    private String issuer = "sentinel-ai";

    @NotBlank
    private String audience = "sentinel-ai-api";

    /** Allowed clock skew when validating exp/nbf, in seconds. */
    @Min(0)
    private long clockSkewSeconds = 30;

    /** Minimum acceptable secret length in characters (HS256 needs >= 32). */
    @Min(32)
    private int minSecretLength = 32;
}
