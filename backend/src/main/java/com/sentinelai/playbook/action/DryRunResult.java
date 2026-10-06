package com.sentinelai.playbook.action;

/**
 * Dry-run preview. {@code allowed=false} means the action would be refused (e.g. a protected
 * target); {@code blastRadius} summarizes impact ("affects 1 user, 0 admins").
 */
public record DryRunResult(boolean allowed, String reason, String summary,
                           int affectedUsers, int affectedAdmins) {

    public String blastRadius() {
        return "affects " + affectedUsers + " user(s), " + affectedAdmins + " admin(s)";
    }
}
