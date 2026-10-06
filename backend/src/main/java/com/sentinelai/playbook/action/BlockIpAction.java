package com.sentinelai.playbook.action;

import com.sentinelai.playbook.TargetPolicy;
import com.sentinelai.playbook.adapter.FirewallAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Block a source IP at the firewall. Refused for protected/internal IPs. */
@Component
@RequiredArgsConstructor
public class BlockIpAction implements ResponseAction {

    private final FirewallAdapter firewall;
    private final TargetPolicy policy;

    @Override
    public String type() {
        return "block_ip";
    }

    @Override
    public boolean destructive() {
        return true;
    }

    @Override
    public DryRunResult dryRun(ActionContext ctx) {
        if (policy.isProtectedIp(ctx.target())) {
            return new DryRunResult(false, "Target IP is protected (internal/allow-listed) and cannot be blocked.",
                    "Block IP " + ctx.target(), 0, 0);
        }
        TargetPolicy.Blast blast = policy.blastForIp(ctx.orgId(), ctx.target());
        return new DryRunResult(true, null, "Block IP " + ctx.target(), blast.users(), blast.admins());
    }

    @Override
    public ExecutionResult execute(ActionContext ctx) {
        boolean was = firewall.isBlocked(ctx.target());
        firewall.block(ctx.target());
        return new ExecutionResult(true, "{\"blocked\":" + was + "}", "{\"blocked\":true}",
                "IP " + ctx.target() + " blocked");
    }

    @Override
    public void rollback(ActionContext ctx) {
        firewall.unblock(ctx.target());
    }
}
