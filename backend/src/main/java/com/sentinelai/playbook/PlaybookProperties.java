package com.sentinelai.playbook;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Set;

/**
 * SOAR playbook configuration: which destructive actions are allowed to execute at all, which
 * targets are protected (never auto-blocked), approval expiry, and adapter timeouts/retries. All
 * overridable per profile / env var.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.playbook")
public class PlaybookProperties {

    /** Approvals (and proposals) expire this many minutes after creation. */
    @Min(1)
    private int expiryMinutes = 30;

    @Min(10)
    private int adapterTimeoutMs = 2000;
    @Min(0)
    private int adapterMaxRetries = 2;

    /** Action types treated as destructive (require the stricter approval path). */
    private Set<String> destructiveActions =
            Set.of("block_ip", "disable_user", "force_password_reset", "revoke_sessions");

    /** Allow-list: only these action types may ever execute. A destructive action not here is refused. */
    private Set<String> allowedActions =
            Set.of("block_ip", "disable_user", "force_password_reset", "revoke_sessions", "add_watchlist");

    /** Protected networks — IPs in these CIDRs can never be blocked (internal network + loopback). */
    private List<String> protectedCidrs =
            List.of("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8");

    /** Additional individual protected IPs (e.g. gateways, scanners). */
    private List<String> protectedIps = List.of();

    public boolean isDestructive(String actionType) {
        return destructiveActions.contains(actionType);
    }

    public boolean isAllowed(String actionType) {
        return allowedActions.contains(actionType);
    }
}
