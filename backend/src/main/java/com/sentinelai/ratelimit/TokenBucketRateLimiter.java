package com.sentinelai.ratelimit;

import com.sentinelai.redis.RedisGateway;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket limiter backed by an atomic Redis Lua script, with an in-memory fallback used when
 * Redis is disabled or unavailable.
 */
@Component
public class TokenBucketRateLimiter implements RateLimiter {

    private static final RedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local tokens_key = KEYS[1]
            local ts_key = KEYS[2]
            local capacity = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local last = tonumber(redis.call('get', tokens_key))
            if last == nil then last = capacity end
            local last_ts = tonumber(redis.call('get', ts_key))
            if last_ts == nil then last_ts = now end
            local delta = math.max(0, now - last_ts) / 1000.0
            local filled = math.min(capacity, last + delta * refill)
            local allowed = 0
            local remaining = filled
            if filled >= 1 then allowed = 1 remaining = filled - 1 end
            local ttl = math.max(1, math.ceil(capacity / refill))
            redis.call('set', tokens_key, remaining, 'EX', ttl)
            redis.call('set', ts_key, now, 'EX', ttl)
            return { allowed, math.floor(remaining) }
            """, List.class);

    private final RedisGateway redis;
    private final Map<String, double[]> local = new ConcurrentHashMap<>(); // key -> {tokens, lastMs}

    public TokenBucketRateLimiter(RedisGateway redis) {
        this.redis = redis;
    }

    @Override
    public Result tryAcquire(String key, int capacity, double refillPerSecond) {
        long now = System.currentTimeMillis();
        var viaRedis = redis.eval(SCRIPT,
                List.of("rl:" + key + ":t", "rl:" + key + ":ts"),
                String.valueOf(capacity), String.valueOf(refillPerSecond), String.valueOf(now));
        if (viaRedis.isPresent()) {
            List<?> r = viaRedis.get();
            boolean allowed = ((Number) r.get(0)).longValue() == 1L;
            long remaining = ((Number) r.get(1)).longValue();
            return result(allowed, remaining, capacity, refillPerSecond);
        }
        return localAcquire(key, capacity, refillPerSecond, now);
    }

    private synchronized Result localAcquire(String key, int capacity, double refill, long now) {
        double[] state = local.computeIfAbsent(key, k -> new double[]{capacity, now});
        double delta = Math.max(0, now - state[1]) / 1000.0;
        double filled = Math.min(capacity, state[0] + delta * refill);
        boolean allowed = filled >= 1;
        double remaining = allowed ? filled - 1 : filled;
        state[0] = remaining;
        state[1] = now;
        return result(allowed, (long) Math.floor(remaining), capacity, refill);
    }

    private Result result(boolean allowed, long remaining, int capacity, double refill) {
        long retry = allowed ? 0 : (long) Math.ceil(1.0 / Math.max(refill, 0.0001));
        return new Result(allowed, remaining, capacity, retry);
    }
}
