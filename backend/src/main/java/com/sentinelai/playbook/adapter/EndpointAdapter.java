package com.sentinelai.playbook.adapter;

/**
 * Enforcement-point seam for endpoint (EDR) actions such as network isolation. A mock ships now; a
 * real EDR integration can replace it later without changing the actions.
 */
public interface EndpointAdapter {

    void isolate(String host);

    void release(String host);

    boolean isIsolated(String host);
}
