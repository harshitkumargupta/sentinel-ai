package com.sentinelai.playbook.action;

import com.sentinelai.playbook.TargetPolicy;
import com.sentinelai.playbook.adapter.IdentityAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Revoke a user's active sessions. Refused for admin accounts (protected). */
@Component
@RequiredArgsConstructor
public class RevokeSessionsAction implements ResponseAction {

    private final IdentityAdapter identity;
    private final TargetPolicy policy;

    @Override public String type() { return "revoke_sessions"; }
    @Override public boolean destructive() { return true; }

    @Override
    public DryRunResult dryRun(ActionContext ctx) {
        if (policy.isAdminUser(ctx.target())) {
            return new DryRunResult(false, "Target is an admin account (protected).",
                    "Revoke sessions for " + ctx.target(), 1, 1);
        }
        return new DryRunResult(true, null, "Revoke sessions for " + ctx.target(), 1, 0);
    }

    @Override
    public ExecutionResult execute(ActionContext ctx) {
        int revoked = identity.revokeSessions(ctx.target());
        return new ExecutionResult(true, "{\"sessions\":\"active\"}",
                "{\"revoked\":" + revoked + "}", "Revoked " + revoked + " session(s) for " + ctx.target());
    }

    @Override
    public void rollback(ActionContext ctx) {
        // Session revocation cannot be undone; rollback is a no-op.
    }
}
