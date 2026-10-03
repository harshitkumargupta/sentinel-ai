package com.sentinelai.detection.engine;

import com.sentinelai.event.domain.EventType;

/** Canonical window-store key: {@code <EVENT_TYPE>|<GROUP_BY>|<value>}. */
public final class WindowKeys {

    private WindowKeys() {
    }

    public static String of(EventType type, GroupBy by, String value) {
        return type.name() + "|" + by.name() + "|" + value;
    }
}
