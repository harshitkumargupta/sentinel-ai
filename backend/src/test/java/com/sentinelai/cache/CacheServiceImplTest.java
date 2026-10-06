package com.sentinelai.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.redis.RedisTestSupport;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Fallback cache (Redis down): caches via in-memory, isolates tenant keys, evicts by prefix. */
class CacheServiceImplTest {

    private final CacheService cache = new CacheServiceImpl(RedisTestSupport.disabledGateway(), new ObjectMapper());
    private final TypeReference<String> STR = new TypeReference<>() {
    };

    @Test
    void loaderRunsOnceThenServesFromCache() {
        AtomicInteger loads = new AtomicInteger();
        String k = "dash:org:1:site:all:summary";
        assertThat(cache.getOrLoad(k, STR, 60, () -> "v" + loads.incrementAndGet())).isEqualTo("v1");
        assertThat(cache.getOrLoad(k, STR, 60, () -> "v" + loads.incrementAndGet())).isEqualTo("v1");
        assertThat(loads.get()).isEqualTo(1);
        assertThat(cache.stats().hits()).isEqualTo(1);
        assertThat(cache.stats().misses()).isEqualTo(1);
    }

    @Test
    void tenantKeysAreIsolated() {
        cache.getOrLoad("dash:org:1:site:all:summary", STR, 60, () -> "org1");
        cache.getOrLoad("dash:org:2:site:all:summary", STR, 60, () -> "org2");
        assertThat(cache.getOrLoad("dash:org:1:site:all:summary", STR, 60, () -> "x")).isEqualTo("org1");
        assertThat(cache.getOrLoad("dash:org:2:site:all:summary", STR, 60, () -> "x")).isEqualTo("org2");
    }

    @Test
    void evictByPrefixClearsTenant() {
        cache.getOrLoad("dash:org:1:site:all:summary", STR, 60, () -> "first");
        cache.evictByPrefix("dash:org:1:");
        AtomicInteger loads = new AtomicInteger();
        assertThat(cache.getOrLoad("dash:org:1:site:all:summary", STR, 60, () -> "reloaded" + loads.incrementAndGet()))
                .isEqualTo("reloaded1");
    }
}
