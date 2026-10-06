package com.sentinelai.auth.service;

import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.domain.RefreshToken;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.dto.TokenResponse;
import com.sentinelai.auth.dto.UserResponse;
import com.sentinelai.auth.repository.RefreshTokenRepository;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.auth.security.JwtService;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.util.Hashing;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.service.SecurityEventRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long DEFAULT_ORG_ID = 1L;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final com.sentinelai.auth.security.JwtProperties jwtProperties;
    private final AuditService auditService;
    private final SecurityEventRecorder eventRecorder;

    @Value("${sentinel.security.max-failed-logins:5}")
    private int maxFailedLogins;
    @Value("${sentinel.security.lockout-minutes:15}")
    private long lockoutMinutes;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Not {@code @Transactional}: failed-attempt counters, self-monitoring events, and audit
     * entries must persist even though the method throws on a bad login. Each repository call
     * therefore commits independently (adequate for this single-instance deployment).
     */
    public TokenResponse login(String username, String rawPassword, String ip) {
        User user = userRepository.findByUsername(username).orElse(null);

        if (user == null) {
            // Unknown user: still record the attempt for self-monitoring.
            eventRecorder.record(DEFAULT_ORG_ID, EventType.FAILED_LOGIN, Severity.LOW, username, ip,
                    "auth/login", "{\"reason\":\"UNKNOWN_USER\"}");
            auditService.record(DEFAULT_ORG_ID, null, "LOGIN_FAILED", "user", null,
                    "{\"username\":\"" + safe(username) + "\",\"reason\":\"UNKNOWN_USER\"}", ip);
            throw new BadCredentialsException("Invalid username or password");
        }

        if (isLocked(user)) {
            eventRecorder.record(user.getOrg().getId(), EventType.FAILED_LOGIN, Severity.MEDIUM,
                    username, ip, "auth/login", "{\"reason\":\"ACCOUNT_LOCKED\"}");
            throw new BadCredentialsException("Account is locked. Try again later.");
        }

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            registerFailure(user, ip);
            throw new BadCredentialsException("Invalid username or password");
        }

        // Success: reset lockout state, stamp login time.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        AppUserPrincipal principal = new AppUserPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = issueRefreshToken(user);

        auditService.record(user.getOrg().getId(), user.getId(), "LOGIN", "user", user.getId(),
                "{\"username\":\"" + safe(username) + "\"}", ip);

        return TokenResponse.of(accessToken, refreshToken, jwtService.getAccessTtlSeconds(),
                UserResponse.from(user));
    }

    // noRollbackFor: on reuse detection we revoke the whole token family and THEN throw 401 — the
    // revocation must commit, not roll back with the exception.
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        String hash = Hashing.sha256Hex(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        User user = stored.getUser();

        // Reuse detection: a token that was already rotated (revoked) is being presented again.
        // This is the classic stolen-refresh-token signal — revoke the entire family and refuse.
        if (stored.isRevoked()) {
            int revoked = refreshTokenRepository.revokeAllForUser(user.getId());
            eventRecorder.record(user.getOrg().getId(), EventType.FAILED_LOGIN, Severity.HIGH,
                    user.getUsername(), null, "auth/refresh",
                    "{\"reason\":\"REFRESH_TOKEN_REUSE\",\"revokedFamily\":" + revoked + "}");
            auditService.record(user.getOrg().getId(), user.getId(), "REFRESH_REUSE_DETECTED", "user",
                    user.getId(), "{\"revokedFamily\":" + revoked + "}", null);
            throw new BadCredentialsException("Invalid refresh token");
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        // Rotate: revoke the used token, issue a fresh pair.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        AppUserPrincipal principal = new AppUserPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal);
        String newRefresh = issueRefreshToken(user);

        return TokenResponse.of(accessToken, newRefresh, jwtService.getAccessTtlSeconds(),
                UserResponse.from(user));
    }

    @Transactional
    public void logout(AppUserPrincipal principal, String ip) {
        refreshTokenRepository.revokeAllForUser(principal.getUserId());
        auditService.record(principal.getOrgId(), principal.getUserId(), "LOGOUT", "user",
                principal.getUserId(), null, ip);
    }

    @Transactional(readOnly = true)
    public UserResponse me(AppUserPrincipal principal) {
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new BadRequestException("User no longer exists"));
        return UserResponse.from(user);
    }

    // --- helpers ---

    private boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
    }

    private void registerFailure(User user, String ip) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        String reason = "BAD_PASSWORD";
        if (attempts >= maxFailedLogins) {
            user.setLockedUntil(Instant.now().plus(lockoutMinutes, ChronoUnit.MINUTES));
            reason = "LOCKED_OUT";
        }
        userRepository.save(user);

        Severity severity = attempts >= maxFailedLogins ? Severity.HIGH : Severity.LOW;
        eventRecorder.record(user.getOrg().getId(), EventType.FAILED_LOGIN, severity,
                user.getUsername(), ip, "auth/login",
                "{\"reason\":\"" + reason + "\",\"attempts\":" + attempts + "}");
        auditService.record(user.getOrg().getId(), user.getId(), "LOGIN_FAILED", "user", user.getId(),
                "{\"reason\":\"" + reason + "\",\"attempts\":" + attempts + "}", ip);
    }

    private String issueRefreshToken(User user) {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(Hashing.sha256Hex(raw))
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenTtl()))
                .revoked(false)
                .build());
        return raw;
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace("\"", "'");
    }
}
