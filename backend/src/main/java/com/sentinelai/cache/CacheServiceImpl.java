package com.sentinelai.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.redis.RedisGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Redis-backed cache-aside with a per-key single-flight lock (stampede guard) and an in-memory
 * fallback when Redis is unavailable. Hit/miss counts feed /api/admin/cache-stats.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheServiceImpl implements CacheService {

    private final RedisGateway redis;
    private final ObjectMapper objectMapper;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final ConcurrentHashMap<String, Lock> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalEntry> local = new ConcurrentHashMap<>();

    private record LocalEntry(String json, long expiresAtMs) {
    }

    @Override
    public <T> T getOrLoad(String key, TypeReference<T> type, long ttlSeconds, Supplier<T> loader) {
        String cached = read(key);
        if (cached != null) {
            hits.incrementAndGet();
            T v = deserialize(cached, type);
            if (v != null) {
                return v;
            }
        }
        misses.incrementAndGet();
        Lock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            String again = read(key); // another thread may have filled it
            if (again != null) {
                T v = deserialize(again, type);
                if (v != null) {
                    return v;
                }
            }
            T value = loader.get();
            write(key, serialize(value), ttlSeconds);
            return value;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void evictByPrefix(String prefix) {
        redis.call(t -> {
            var keys = t.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                t.delete(keys);
            }
            return true;
        }, "cache.evict");
        local.keySet().removeIf(k -> k.startsWith(prefix));
    }

    @Override
    public Stats stats() {
        long h = hits.get();
        long m = misses.get();
        double rate = (h + m) == 0 ? 0.0 : Math.round((double) h / (h + m) * 1000.0) / 1000.0;
        return new Stats(h, m, rate);
    }

    private String read(String key) {
        var fromRedis = redis.call(t -> t.opsForValue().get(key), "cache.get");
        if (fromRedis.isPresent()) {
            return fromRedis.get();
        }
        LocalEntry e = local.get(key);
        if (e != null && e.expiresAtMs() > System.currentTimeMillis()) {
            return e.json();
        }
        return null;
    }

    private void write(String key, String json, long ttlSeconds) {
        var ok = redis.call(t -> {
            t.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
            return true;
        }, "cache.put");
        if (ok.isEmpty()) {
            local.put(key, new LocalEntry(json, System.currentTimeMillis() + ttlSeconds * 1000));
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T deserialize(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }
}
