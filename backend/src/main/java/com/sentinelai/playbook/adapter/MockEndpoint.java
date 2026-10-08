package com.sentinelai.playbook.adapter;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory EDR: records isolated hosts only — no real network change. Supports fault injection. */
@Component
public class MockEndpoint implements EndpointAdapter {

    private final Set<String> isolated = ConcurrentHashMap.newKeySet();
    private final AtomicInteger failuresToInject = new AtomicInteger(0);

    public void failNext(int n) {
        failuresToInject.set(n);
    }

    private void maybeFail() {
        if (failuresToInject.get() > 0 && failuresToInject.getAndDecrement() > 0) {
            throw new AdapterException("mock endpoint transient failure");
        }
    }

    @Override
    public void isolate(String host) {
        maybeFail();
        isolated.add(host);
    }

    @Override
    public void release(String host) {
        maybeFail();
        isolated.remove(host);
    }

    @Override
    public boolean isIsolated(String host) {
        return isolated.contains(host);
    }
}
