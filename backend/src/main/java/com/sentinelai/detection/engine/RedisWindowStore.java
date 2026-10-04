package com.sentinelai.detection.engine;

import com.sentinelai.redis.RedisGateway;
import com.sentinelai.redis.RedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis-backed {@link WindowStore} using a sorted set per key (score = epoch millis). Chosen over
 * {@link InMemoryWindowStore} when {@code sentinel.redis.enabled=true}; on any Redis failure it
 * transparently falls back to the in-memory store so detection keeps working.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "sentinel.redis", name = "enabled", havingValue = "true")
public class RedisWindowStore implements WindowStore {

    private static final long RETENTION_SECONDS = 24 * 3600L;

    private final RedisGateway redis;
    private final RedisProperties props;
    private final InMemoryWindowStore fallback;
    private final AtomicLong seq = new AtomicLong();

    public RedisWindowStore(RedisGateway redis, RedisProperties props, InMemoryWindowStore fallback) {
        this.redis = redis;
        this.props = props;
        this.fallback = fallback;
    }

    @Override
    public void record(String key, Instant timestamp) {
        String rk = "win:" + key;
        double score = timestamp.toEpochMilli();
        String member = timestamp.toEpochMilli() + ":" + seq.incrementAndGet();
        var ok = redis.call(t -> {
            t.opsForZSet().add(rk, member, score);
            t.opsForZSet().removeRangeByScore(rk, 0, score - RETENTION_SECONDS * 1000.0);
            t.expire(rk, java.time.Duration.ofSeconds(props.getWindowTtlSeconds()));
            return Boolean.TRUE;
        }, "window.record");
        if (ok.isEmpty()) {
            fallback.record(key, timestamp);
        }
    }

    @Override
    public long count(String key, Instant from, Instant to) {
        var count = redis.call(t -> t.opsForZSet().count("win:" + key,
                (double) from.toEpochMilli(), (double) to.toEpochMilli()), "window.count");
        return count.orElseGet(() -> fallback.count(key, from, to));
    }
}
