package com.sentinelai.playbook.action;

import com.sentinelai.playbook.adapter.IdentityAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Add an entity to the watchlist. Non-destructive (passive monitoring), always allowed. */
@Component
@RequiredArgsConstructor
public class AddWatchlistAction implements ResponseAction {

    private final IdentityAdapter identity;

    @Override public String type() { return "add_watchlist"; }
    @Override public boolean destructive() { return false; }

    @Override
    public DryRunResult dryRun(ActionContext ctx) {
        return new DryRunResult(true, null, "Add " + ctx.target() + " to watchlist", 1, 0);
    }

    @Override
    public ExecutionResult execute(ActionContext ctx) {
        boolean was = identity.isWatchlisted(ctx.target());
        identity.addWatchlist(ctx.target());
        return new ExecutionResult(true, "{\"watchlisted\":" + was + "}", "{\"watchlisted\":true}",
                ctx.target() + " added to watchlist");
    }

    @Override
    public void rollback(ActionContext ctx) {
        identity.removeWatchlist(ctx.target());
    }
}
