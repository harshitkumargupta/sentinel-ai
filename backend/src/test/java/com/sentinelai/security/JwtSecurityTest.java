package com.sentinelai.security;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.support.IntegrationTestSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JWT verification hardening: forged/tampered/expired/wrong-issuer/wrong-audience tokens — and the
 * classic {@code alg:none} token — are all rejected with 401, while a correctly signed token is
 * accepted. Guards against signature-stripping and algorithm-confusion attacks.
 */
class JwtSecurityTest extends IntegrationTestSupport {

    private static final String SECRET = "test-only-sentinel-ai-secret-key-0123456789-abcdefghij";
    private static final String ISSUER = "sentinel-ai";
    private static final String AUDIENCE = "sentinel-ai-api";

    private SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String token(String iss, String aud, Date exp, SecretKey signingKey) {
        return Jwts.builder()
                .issuer(iss).audience().add(aud).and()
                .subject("alice")
                .claims(Map.of("uid", 1, "org", 1, "role", "ADMIN"))
                .issuedAt(new Date()).notBefore(new Date())
                .expiration(exp)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        createUser("alice", Role.ADMIN);
        String jwt = token(ISSUER, AUDIENCE, new Date(System.currentTimeMillis() + 60_000), key());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    void noneAlgTokenIsRejected() throws Exception {
        // header {"alg":"none"} + our claims + empty signature
        String header = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = b64("{\"sub\":\"alice\",\"iss\":\"" + ISSUER + "\",\"aud\":\"" + AUDIENCE + "\"}");
        String forged = header + "." + payload + ".";
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedSignatureIsRejected() throws Exception {
        createUser("alice", Role.ADMIN);
        String jwt = token(ISSUER, AUDIENCE, new Date(System.currentTimeMillis() + 60_000), key());
        String tampered = jwt.substring(0, jwt.length() - 2) + (jwt.endsWith("a") ? "bb" : "aa");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSigningKeyIsRejected() throws Exception {
        SecretKey attacker = Keys.hmacShaKeyFor(
                "another-32byte-minimum-attacker-secret-key-123456".getBytes(StandardCharsets.UTF_8));
        String jwt = token(ISSUER, AUDIENCE, new Date(System.currentTimeMillis() + 60_000), attacker);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String jwt = token(ISSUER, AUDIENCE, new Date(System.currentTimeMillis() - 120_000), key());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceIsRejected() throws Exception {
        String jwt = token(ISSUER, "some-other-api", new Date(System.currentTimeMillis() + 60_000), key());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        String jwt = token("evil-issuer", AUDIENCE, new Date(System.currentTimeMillis() + 60_000), key());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
