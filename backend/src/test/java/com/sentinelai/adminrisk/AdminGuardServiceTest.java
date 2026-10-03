package com.sentinelai.adminrisk;

import com.sentinelai.adminrisk.GuardDecision.Decision;
import com.sentinelai.adminrisk.domain.AdminBaseline;
import com.sentinelai.adminrisk.domain.AdminBaselineId;
import com.sentinelai.adminrisk.domain.AdminBaselineRepository;
import com.sentinelai.auth.domain.RefreshToken;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.RefreshTokenRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.util.Hashing;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AdminGuardServiceTest extends IntegrationTestSupport {

    @Autowired private AdminGuardService guard;
    @Autowired private AdminBaselineRepository baselineRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    private AppUserPrincipal admin(String username) {
        User u = createUser(username, Role.ADMIN);
        // Baseline: known US / 1.1.1.1 and all hours typical (so TimeFactor stays quiet).
        baselineRepository.save(AdminBaseline.builder().id(new AdminBaselineId(u.getId(), "known_countries")).data("[\"US\"]").build());
        baselineRepository.save(AdminBaseline.builder().id(new AdminBaselineId(u.getId(), "known_ips")).data("[\"1.1.1.1\"]").build());
        baselineRepository.save(AdminBaseline.builder().id(new AdminBaselineId(u.getId(), "known_sites")).data("[1]").build());
        baselineRepository.save(AdminBaseline.builder().id(new AdminBaselineId(u.getId(), "typical_hours"))
                .data("[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23]").build());
        return new AppUserPrincipal(u);
    }

    @Test
    void lowRiskIsAllowed() {
        var d = guard.guard(admin("gl_low"), "VIEW", "user", 1L, null, null, "1.1.1.1", "US", null, 1L, false);
        assertThat(d.decision()).isEqualTo(Decision.ALLOW);
        assertThat(d.band()).isEqualTo(RiskBand.LOW);
    }

    @Test
    void mediumRiskRequiresStepUpThenAllows() {
        // USER_DISABLE(20) + new IP(10) = 30 -> MEDIUM
        var without = guard.guard(admin("gl_med"), "USER_DISABLE", "user", 1L, null, null, "9.9.9.9", "US", null, 1L, false);
        assertThat(without.decision()).isEqualTo(Decision.STEP_UP);
        var with = guard.guard(admin("gl_med2"), "USER_DISABLE", "user", 1L, null, null, "9.9.9.9", "US", null, 1L, true);
        assertThat(with.decision()).isEqualTo(Decision.ALLOW);
    }

    @Test
    void highRiskCreatesPendingApproval() {
        // USER_DISABLE(20) + new country(25) + new IP(10) = 55 -> HIGH
        var d = guard.guard(admin("gl_high"), "USER_DISABLE", "user", 1L, null, null, "9.9.9.9", "CN", null, 1L, false);
        assertThat(d.decision()).isEqualTo(Decision.PENDING_APPROVAL);
        assertThat(d.pendingActionId()).isNotNull();
    }

    @Test
    void criticalRiskBlocksAndRevokesSessions() {
        AppUserPrincipal actor = admin("gl_crit");
        RefreshToken token = refreshTokenRepository.save(RefreshToken.builder()
                .user(refreshUser(actor))
                .tokenHash(Hashing.sha256Hex("sess-" + actor.getUserId()))
                .expiresAt(Instant.now().plusSeconds(3600)).revoked(false).build());

        // USER_DISABLE(20)+new country(25)+new IP(10)+privilege escalation(35) = 90 -> CRITICAL
        var d = guard.guard(actor, "USER_DISABLE", "user", 1L, "{\"role\":\"VIEWER\"}",
                "{\"role\":\"ADMIN\"}", "9.9.9.9", "CN", null, 1L, false);

        assertThat(d.decision()).isEqualTo(Decision.BLOCKED);
        assertThat(refreshTokenRepository.findById(token.getId()).orElseThrow().isRevoked()).isTrue();
    }

    private User refreshUser(AppUserPrincipal p) {
        return userRepository.findById(p.getUserId()).orElseThrow();
    }
}
