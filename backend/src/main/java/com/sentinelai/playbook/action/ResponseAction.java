package com.sentinelai.playbook.action;

/**
 * A response action strategy. New actions are added by implementing this interface and a config
 * entry — the engine and state machine don't change. Actions run against adapter interfaces
 * (firewall / identity), never real systems directly, so integrations can be swapped later.
 */
public interface ResponseAction {

    /** Stable type key, matching the recommendation action (e.g. {@code block_ip}). */
    String type();

    /** Destructive actions take the stricter approval path and must be on the allow-list to run. */
    boolean destructive();

    /** Preview: what would change and the blast radius; {@code allowed=false} for protected targets. */
    DryRunResult dryRun(ActionContext ctx);

    /** Apply the action, returning before/after state for rollback and audit. */
    ExecutionResult execute(ActionContext ctx);

    /** Undo a previously-executed action (best effort for irreversible ones). */
    void rollback(ActionContext ctx);
}
