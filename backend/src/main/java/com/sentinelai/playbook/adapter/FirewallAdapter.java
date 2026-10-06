package com.sentinelai.playbook.adapter;

/**
 * Enforcement-point seam for IP blocking. A mock implementation ships now; a real firewall/WAF
 * integration can replace it later without changing the actions. Implementations may throw to
 * simulate transient failures — the executor applies timeouts and retries.
 */
public interface FirewallAdapter {

    void block(String ip);

    void unblock(String ip);

    boolean isBlocked(String ip);
}
