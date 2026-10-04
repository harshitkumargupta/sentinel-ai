package com.sentinelai.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Evicts a tenant's dashboard cache when its incidents/alerts change. */
@Component
@RequiredArgsConstructor
public class CacheInvalidationListener {

    private final CacheService cacheService;

    @EventListener
    public void onIncidentsChanged(IncidentsChangedEvent event) {
        cacheService.evictByPrefix("dash:org:" + event.orgId() + ":");
    }
}
