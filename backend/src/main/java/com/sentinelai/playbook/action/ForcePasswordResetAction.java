package com.sentinelai.playbook.action;

import com.sentinelai.playbook.TargetPolicy;
import com.sentinelai.playbook.adapter.IdentityAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Force a password reset for a user. Refused for admin accounts (protected). */
@Component
@RequiredArgsConstructor
public class ForcePasswordResetAction implements ResponseAction {

    private final IdentityAdapter identity;
    private final TargetPolicy policy;

    @Override public String type() { return "force_password_reset"; }
    @Override public boolean destructive() { return true; }

    @Override
    public DryRunResult dryRun(ActionContext ctx) {
        if (policy.isAdminUser(ctx.target())) {
            return new DryRunResult(false, "Target is an admin account (protected).",
                    "Force password reset for " + ctx.target(), 1, 1);
        }
        return new DryRunResult(true, null, "Force password reset for " + ctx.target(), 1, 0);
    }

    @Override
    public ExecutionResult execute(ActionContext ctx) {
        identity.forcePasswordReset(ctx.target());
        return new ExecutionResult(true, "{\"resetRequired\":false}", "{\"resetRequired\":true}",
                "Password reset forced for " + ctx.target());
    }

    @Override
    public void rollback(ActionContext ctx) {
        // A forced reset cannot be meaningfully undone; rollback is a no-op (recorded in the timeline).
    }
}
