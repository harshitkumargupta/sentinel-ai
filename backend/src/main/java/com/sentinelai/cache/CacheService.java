package com.sentinelai.cache;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.function.Supplier;

/**
 * Cache-aside with a stampede guard. Keys must be tenant-scoped (include org/site) by the caller.
 * Degrades to simply calling the loader if the cache backend is unavailable.
 */
public interface CacheService {

    <T> T getOrLoad(String key, TypeReference<T> type, long ttlSeconds, Supplier<T> loader);

    /** Evict every key under a prefix (e.g. a tenant's dashboard namespace). */
    void evictByPrefix(String prefix);

    Stats stats();

    record Stats(long hits, long misses, double hitRate) {
    }
}
