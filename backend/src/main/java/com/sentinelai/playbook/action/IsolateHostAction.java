package com.sentinelai.playbook.action;

import com.sentinelai.playbook.TargetPolicy;
import com.sentinelai.playbook.adapter.EndpointAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Network-isolate an endpoint via the EDR adapter. Refused for protected hosts. */
@Component
@RequiredArgsConstructor
public class IsolateHostAction implements ResponseAction {

    private final EndpointAdapter endpoint;
    private final TargetPolicy policy;

    @Override
    public String type() {
        return "isolate_host";
    }

    @Override
    public boolean destructive() {
        return true;
    }

    @Override
    public DryRunResult dryRun(ActionContext ctx) {
        if (policy.isProtectedHost(ctx.target())) {
            return new DryRunResult(false, "Target host is protected (critical infrastructure) and cannot be isolated.",
                    "Isolate host " + ctx.target(), 0, 0);
        }
        TargetPolicy.Blast blast = policy.blastForHost(ctx.orgId(), ctx.target());
        return new DryRunResult(true, null, "Isolate host " + ctx.target() + " from the network",
                blast.users(), blast.admins());
    }

    @Override
    public ExecutionResult execute(ActionContext ctx) {
        boolean was = endpoint.isIsolated(ctx.target());
        endpoint.isolate(ctx.target());
        return new ExecutionResult(true, "{\"isolated\":" + was + "}", "{\"isolated\":true}",
                "Host " + ctx.target() + " isolated");
    }

    @Override
    public void rollback(ActionContext ctx) {
        endpoint.release(ctx.target());
    }
}
