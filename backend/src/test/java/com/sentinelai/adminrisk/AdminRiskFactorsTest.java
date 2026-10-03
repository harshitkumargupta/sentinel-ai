package com.sentinelai.adminrisk;

import com.sentinelai.adminrisk.factor.ActionSensitivityFactor;
import com.sentinelai.adminrisk.factor.BurstFactor;
import com.sentinelai.adminrisk.factor.NewIpCountryDeviceFactor;
import com.sentinelai.adminrisk.factor.PeerDeviationFactor;
import com.sentinelai.adminrisk.factor.PrivilegeEscalationFactor;
import com.sentinelai.adminrisk.factor.TimeFactor;
import com.sentinelai.adminrisk.factor.UnusualSiteFactor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRiskFactorsTest {

    private final AdminRiskProperties props = new AdminRiskProperties();

    private AdminBaselines baselines() {
        return new AdminBaselines(Set.of("US"), Set.of("1.1.1.1"), Set.of("dev-a"),
                Set.of(9, 10, 11, 12, 13, 14, 15, 16, 17), Set.of(1L));
    }

    private AdminActionContext ctx(String action, String country, String ip, String device, Long siteId,
                                   Instant at, String before, String after, long recent, double peer) {
        return new AdminActionContext(1L, 1L, "admin", action, "user", 9L, ip, country, device, siteId,
                at, before, after, baselines(), recent, peer);
    }

    @Test
    void timeFactorFiresOffHours() {
        var at = Instant.parse("2026-02-01T03:00:00Z"); // 03:00 UTC, outside 9-17
        assertThat(new TimeFactor().score(ctx("VIEW", "US", "1.1.1.1", "dev-a", 1L, at, null, null, 0, 0), props).points())
                .isEqualTo(props.getOffHoursPoints());
        var day = Instant.parse("2026-02-01T10:00:00Z");
        assertThat(new TimeFactor().score(ctx("VIEW", "US", "1.1.1.1", "dev-a", 1L, day, null, null, 0, 0), props).points())
                .isZero();
    }

    @Test
    void newOriginFactorFlagsUnknownCountryIpDevice() {
        var at = Instant.parse("2026-02-01T10:00:00Z");
        int pts = new NewIpCountryDeviceFactor()
                .score(ctx("VIEW", "CN", "2.2.2.2", "dev-z", 1L, at, null, null, 0, 0), props).points();
        assertThat(pts).isEqualTo(props.getNewCountryPoints() + props.getNewIpPoints() + props.getNewDevicePoints());
    }

    @Test
    void sensitiveActionScores() {
        var at = Instant.parse("2026-02-01T10:00:00Z");
        assertThat(new ActionSensitivityFactor().score(ctx("USER_DISABLE", "US", "1.1.1.1", "dev-a", 1L, at, null, null, 0, 0), props).points())
                .isEqualTo(props.getSensitiveActionPoints());
        assertThat(new ActionSensitivityFactor().score(ctx("VIEW", "US", "1.1.1.1", "dev-a", 1L, at, null, null, 0, 0), props).points())
                .isZero();
    }

    @Test
    void burstFactorFiresAboveThreshold() {
        var at = Instant.parse("2026-02-01T10:00:00Z");
        assertThat(new BurstFactor().score(ctx("USER_DISABLE", "US", "1.1.1.1", "dev-a", 1L, at, null, null, 6, 0), props).points())
                .isEqualTo(props.getBurstPoints());
    }

    @Test
    void privilegeEscalationFactorFiresOnAdminGrant() {
        var at = Instant.parse("2026-02-01T10:00:00Z");
        assertThat(new PrivilegeEscalationFactor().score(
                ctx("ROLE_CHANGE", "US", "1.1.1.1", "dev-a", 1L, at, "{\"role\":\"VIEWER\"}", "{\"role\":\"ADMIN\"}", 0, 0), props).points())
                .isEqualTo(props.getPrivilegeEscalationPoints());
    }

    @Test
    void peerDeviationFactorFiresWhenFarAbovePeers() {
        var at = Instant.parse("2026-02-01T10:00:00Z");
        assertThat(new PeerDeviationFactor().score(ctx("VIEW", "US", "1.1.1.1", "dev-a", 1L, at, null, null, 10, 2.0), props).points())
                .isEqualTo(props.getPeerDeviationPoints());
    }

    @Test
    void unusualSiteFactorFiresForUnknownSite() {
        var at = Instant.parse("2026-02-01T10:00:00Z");
        assertThat(new UnusualSiteFactor().score(ctx("VIEW", "US", "1.1.1.1", "dev-a", 2L, at, null, null, 0, 0), props).points())
                .isEqualTo(props.getUnusualSitePoints());
    }

    @Test
    void serviceComposesDeterministicScoreAndBand() {
        var service = new AdminRiskService(List.of(new TimeFactor(), new NewIpCountryDeviceFactor(),
                new ActionSensitivityFactor(), new BurstFactor(), new PrivilegeEscalationFactor(),
                new PeerDeviationFactor(), new UnusualSiteFactor()), props);
        var at = Instant.parse("2026-02-01T10:00:00Z");
        var c = ctx("USER_DISABLE", "CN", "2.2.2.2", "dev-a", 1L, at, null, null, 0, 0);
        AdminRiskResult a = service.assess(c);
        AdminRiskResult b = service.assess(c);
        assertThat(a.score()).isEqualTo(b.score());
        // sensitive(20) + newCountry(25) + newIp(10) = 55 -> HIGH
        assertThat(a.score()).isEqualTo(55);
        assertThat(a.band()).isEqualTo(RiskBand.HIGH);
    }
}
