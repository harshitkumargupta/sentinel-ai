package com.sentinelai.common.config;

import com.sentinelai.ai.AiProperties;
import com.sentinelai.auth.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Fails the application fast, in the {@code prod} profile only, when a required secret is missing or
 * obviously weak. Dev/test keep their in-repo defaults; production must supply real secrets via the
 * environment. DB credentials are already enforced by unresolved {@code ${DB_PASSWORD}} placeholders
 * in {@code application-prod.yml}; this covers the JWT secret strength and the LLM API key.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class StartupSecretsValidator {

    /** Known dev-only secrets that must never reach production. */
    private static final Set<String> FORBIDDEN_JWT_SECRETS = Set.of(
            "dev-only-sentinel-ai-secret-key-change-me-0123456789",
            "test-only-sentinel-ai-secret-key-0123456789-abcdefghij");

    private final JwtProperties jwt;
    private final AiProperties ai;

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if (jwt.getSecret() == null || jwt.getSecret().length() < jwt.getMinSecretLength()) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + jwt.getMinSecretLength() + " characters in prod");
        }
        if (FORBIDDEN_JWT_SECRETS.contains(jwt.getSecret())) {
            throw new IllegalStateException("JWT_SECRET is a known dev/test default; set a real secret in prod");
        }
        if (ai.isEnabled() && "http".equalsIgnoreCase(ai.getProvider())) {
            String key = System.getenv("LLM_API_KEY");
            if (key == null || key.isBlank()) {
                throw new IllegalStateException(
                        "LLM_API_KEY must be set when sentinel.ai.enabled=true with the http provider");
            }
        }
        log.info("Startup secret validation passed (prod profile)");
    }
}
