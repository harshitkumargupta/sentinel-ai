package com.sentinelai.playbook;

import com.sentinelai.asset.Asset;
import com.sentinelai.asset.AssetCriticality;
import com.sentinelai.asset.AssetRepository;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.RefreshTokenRepository;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.event.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Impact preview for a response action, shown in the dry-run and audited: which users (and admins)
 * it affects, their active SentinelAI sessions, the hosts and inventoried assets involved with their
 * criticality, and an impact level. HIGH impact (an admin, a HIGH/CRITICAL asset, or ≥ 10 users)
 * requires an explicit confirmation to execute.
 */
@Service
@RequiredArgsConstructor
public class BlastRadiusService {

    public enum Impact { LOW, MEDIUM, HIGH }

    public record AssetImpact(String asset, String criticality, String type, String environment) {
    }

    public record Preview(List<String> users, int admins, int activeSessions, List<String> hosts, List<AssetImpact> assets,
                          boolean targetIsAdmin, boolean targetIsCriticalAsset, Impact impact, boolean requiresConfirmation,
                          List<String> reasons) {
    }

    static final int MANY_USERS = 10;

    private final SecurityEventRepository events;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AssetRepository assets;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Preview preview(Long orgId, String actionType, String target) {
        Set<String> affectedUsers = new LinkedHashSet<>();
        Set<String> hosts = new LinkedHashSet<>();
        Set<Asset> involved = new LinkedHashSet<>();
        boolean targetIsAdmin = false;
        switch (actionType) {
            case "block_ip" -> {
                affectedUsers.addAll(events.distinctUsernamesByOrgAndIp(orgId, target));
                hosts.addAll(hostsWhere(orgId, "source_ip = :t", target));
                assets.findByOrg_IdAndIp(orgId, target).ifPresent(involved::add);
            }
            case "isolate_host" -> {
                affectedUsers.addAll(events.distinctUsernamesByOrgAndEntityKey(orgId, "host:" + target));
                hosts.add(target);
                assets.findByOrg_IdAndHostnameIgnoreCase(orgId, target).ifPresent(involved::add);
            }
            default -> { // user-scoped: disable_user, force_password_reset, revoke_sessions, add_watchlist
                affectedUsers.add(target);
                hosts.addAll(hostsWhere(orgId, "username = :t", target));
                targetIsAdmin = isAdmin(target);
            }
        }
        hosts.forEach(h -> assets.findByOrg_IdAndHostnameIgnoreCase(orgId, h).ifPresent(involved::add));

        int admins = (int) affectedUsers.stream().filter(this::isAdmin).count();
        int sessions = affectedUsers.stream().map(u -> users.findByUsername(u).orElse(null))
                .filter(java.util.Objects::nonNull).mapToInt(this::activeSessions).sum();
        List<AssetImpact> assetImpacts = involved.stream().map(a -> new AssetImpact(a.label(), a.getCriticality().name(),
                a.getType().name(), a.getEnvironment().name())).toList();
        boolean criticalAsset = involved.stream().anyMatch(a -> a.getCriticality() == AssetCriticality.CRITICAL
                || a.getCriticality() == AssetCriticality.HIGH);

        List<String> reasons = new ArrayList<>();
        Impact impact = Impact.LOW;
        if (targetIsAdmin || admins > 0) {
            reasons.add("affects an administrator account");
            impact = Impact.HIGH;
        }
        if (criticalAsset) {
            reasons.add("touches a HIGH/CRITICAL asset");
            impact = Impact.HIGH;
        }
        if (affectedUsers.size() >= MANY_USERS) {
            reasons.add("affects " + affectedUsers.size() + " users");
            impact = Impact.HIGH;
        }
        if (impact == Impact.LOW && (affectedUsers.size() >= 3 || !involved.isEmpty() || sessions > 0)) {
            reasons.add(affectedUsers.size() >= 3 ? "affects several users" : !involved.isEmpty() ? "touches an inventoried asset" : "has active sessions");
            impact = Impact.MEDIUM;
        }
        if (reasons.isEmpty()) {
            reasons.add("no admins, critical assets or active sessions involved");
        }
        return new Preview(affectedUsers.stream().limit(20).toList(), admins, sessions, hosts.stream().limit(20).toList(),
                assetImpacts, targetIsAdmin, criticalAsset, impact, impact == Impact.HIGH, reasons);
    }

    private List<String> hostsWhere(Long orgId, String condition, String value) {
        return jdbc.queryForList("select distinct substring(entity_key, 6) from security_events where org_id = :org and "
                + condition + " and entity_key like 'host:%' limit 20", new MapSqlParameterSource("org", orgId).addValue("t", value), String.class);
    }

    private boolean isAdmin(String username) {
        return username != null && users.findByUsername(username).map(u -> u.getRole() == Role.ADMIN).orElse(false);
    }

    private int activeSessions(User u) {
        var now = clock.instant();
        return (int) refreshTokens.findByUser_IdOrderByIdDesc(u.getId()).stream()
                .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(now)).count();
    }
}
