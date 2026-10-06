package com.sentinelai.playbook.adapter;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory firewall. Supports fault injection so tests can exercise retry/failure handling. */
@Component
public class MockFirewall implements FirewallAdapter {

    private final Set<String> blocked = ConcurrentHashMap.newKeySet();
    private final AtomicInteger failuresToInject = new AtomicInteger(0);

    /** Make the next {@code n} mutating calls throw, to simulate a flaky enforcement point. */
    public void failNext(int n) {
        failuresToInject.set(n);
    }

    private void maybeFail() {
        if (failuresToInject.get() > 0 && failuresToInject.getAndDecrement() > 0) {
            throw new AdapterException("mock firewall transient failure");
        }
    }

    @Override
    public void block(String ip) {
        maybeFail();
        blocked.add(ip);
    }

    @Override
    public void unblock(String ip) {
        maybeFail();
        blocked.remove(ip);
    }

    @Override
    public boolean isBlocked(String ip) {
        return blocked.contains(ip);
    }
}
