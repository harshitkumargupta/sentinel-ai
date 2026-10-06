package com.sentinelai.playbook.adapter;

/**
 * Enforcement-point seam for identity actions (disable, password reset, session revocation,
 * watchlist). A mock implementation ships now; a real IdP integration can replace it later.
 */
public interface IdentityAdapter {

    void disableUser(String username);

    void enableUser(String username);

    boolean isDisabled(String username);

    void forcePasswordReset(String username);

    int revokeSessions(String username);

    void addWatchlist(String target);

    void removeWatchlist(String target);

    boolean isWatchlisted(String target);
}
