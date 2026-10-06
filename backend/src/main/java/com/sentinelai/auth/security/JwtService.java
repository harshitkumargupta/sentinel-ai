package com.sentinelai.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Issues and validates stateless JWT access tokens. Refresh tokens are opaque random strings
 * (hashed in the DB), not JWTs.
 *
 * <p>Hardening: the key is HMAC-SHA256 and {@code verifyWith(SecretKey)} restricts verification to
 * HMAC — a token advertising {@code alg:none} or an asymmetric algorithm is rejected (no algorithm
 * confusion). The issuer and audience are pinned and required, exp/nbf are validated with a small
 * configurable clock skew, and any malformed/expired/forged token resolves to "not authenticated"
 * rather than leaking a reason.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        if (props.getSecret() == null || props.getSecret().length() < props.getMinSecretLength()) {
            throw new IllegalStateException("sentinel.jwt.secret (env JWT_SECRET) must be set and at least "
                    + props.getMinSecretLength() + " characters");
        }
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
        this.props = props;
    }

    public String generateAccessToken(AppUserPrincipal principal) {
        Date now = new Date();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .audience().add(props.getAudience()).and()
                .subject(principal.getUsername())
                .claims(Map.of(
                        "uid", principal.getUserId(),
                        "org", principal.getOrgId(),
                        "role", principal.getRole().name()))
                .issuedAt(now)
                .notBefore(now)
                .expiration(new Date(now.getTime() + props.getAccessTokenTtl()))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long getAccessTtlSeconds() {
        return props.getAccessTokenTtl() / 1000;
    }

    /**
     * @return the username (subject) if the token is valid, or {@code null} if invalid/expired.
     */
    public String extractUsername(String token) {
        try {
            Jws<Claims> jws = parse(token);
            // Defence in depth: reject anything that is not the signing algorithm we issue.
            if (!"HS256".equals(jws.getHeader().getAlgorithm())) {
                return null;
            }
            return jws.getPayload().getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.getIssuer())
                .requireAudience(props.getAudience())
                .clockSkewSeconds(props.getClockSkewSeconds())
                .build()
                .parseSignedClaims(token);
    }
}
