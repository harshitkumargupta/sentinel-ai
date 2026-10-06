package com.sentinelai.playbook.adapter;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory identity provider. Supports fault injection for retry/failure tests. */
@Component
public class MockIdentity implements IdentityAdapter {

    private final Set<String> disabled = ConcurrentHashMap.newKeySet();
    private final Set<String> watchlist = ConcurrentHashMap.newKeySet();
    private final Set<String> passwordResetRequired = ConcurrentHashMap.newKeySet();
    private final AtomicInteger failuresToInject = new AtomicInteger(0);

    public void failNext(int n) {
        failuresToInject.set(n);
    }

    private void maybeFail() {
        if (failuresToInject.get() > 0 && failuresToInject.getAndDecrement() > 0) {
            throw new AdapterException("mock identity transient failure");
        }
    }

    @Override
    public void disableUser(String username) {
        maybeFail();
        disabled.add(username);
    }

    @Override
    public void enableUser(String username) {
        maybeFail();
        disabled.remove(username);
    }

    @Override
    public boolean isDisabled(String username) {
        return disabled.contains(username);
    }

    @Override
    public void forcePasswordReset(String username) {
        maybeFail();
        passwordResetRequired.add(username);
    }

    @Override
    public int revokeSessions(String username) {
        maybeFail();
        return 1; // mock: pretend one active session was revoked
    }

    @Override
    public void addWatchlist(String target) {
        maybeFail();
        watchlist.add(target);
    }

    @Override
    public void removeWatchlist(String target) {
        maybeFail();
        watchlist.remove(target);
    }

    @Override
    public boolean isWatchlisted(String target) {
        return watchlist.contains(target);
    }

    public boolean isPasswordResetRequired(String username) {
        return passwordResetRequired.contains(username);
    }
}
