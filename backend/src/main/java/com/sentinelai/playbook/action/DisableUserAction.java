package com.sentinelai.playbook.action;

import com.sentinelai.playbook.TargetPolicy;
import com.sentinelai.playbook.adapter.IdentityAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Disable a user account. Refused for admin accounts (protected). */
@Component
@RequiredArgsConstructor
public class DisableUserAction implements ResponseAction {

    private final IdentityAdapter identity;
    private final TargetPolicy policy;

    @Override public String type() { return "disable_user"; }
    @Override public boolean destructive() { return true; }

    @Override
    public DryRunResult dryRun(ActionContext ctx) {
        if (policy.isAdminUser(ctx.target())) {
            return new DryRunResult(false, "Target is an admin account (protected) and cannot be disabled.",
                    "Disable user " + ctx.target(), 1, 1);
        }
        return new DryRunResult(true, null, "Disable user " + ctx.target(), 1, 0);
    }

    @Override
    public ExecutionResult execute(ActionContext ctx) {
        boolean was = identity.isDisabled(ctx.target());
        identity.disableUser(ctx.target());
        return new ExecutionResult(true, "{\"disabled\":" + was + "}", "{\"disabled\":true}",
                "User " + ctx.target() + " disabled");
    }

    @Override
    public void rollback(ActionContext ctx) {
        identity.enableUser(ctx.target());
    }
}
