package com.sentinelai.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Issues and validates stateless JWT access tokens. Refresh tokens are opaque random strings
 * (hashed in the DB), not JWTs.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlMs;

    public JwtService(
            @Value("${sentinel.jwt.secret:}") String secret,
            @Value("${sentinel.jwt.access-token-ttl:900000}") long accessTtlMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "sentinel.jwt.secret (env JWT_SECRET) must be set and at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMs = accessTtlMs;
    }

    public String generateAccessToken(AppUserPrincipal principal) {
        Date now = new Date();
        return Jwts.builder()
                .subject(principal.getUsername())
                .claims(Map.of(
                        "uid", principal.getUserId(),
                        "org", principal.getOrgId(),
                        "role", principal.getRole().name()))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTtlMs))
                .signWith(key)
                .compact();
    }

    public long getAccessTtlSeconds() {
        return accessTtlMs / 1000;
    }

    /**
     * @return the username (subject) if the token is valid, or null if invalid/expired.
     */
    public String extractUsername(String token) {
        try {
            return parse(token).getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
