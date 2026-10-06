package com.sentinelai.redis;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Single seam for all Redis use. Every call degrades gracefully: if Redis is disabled, absent, or
 * throws, it returns empty, increments a metric, and logs once (with the request trace id) until the
 * next success — the caller then uses its in-memory fallback. It never propagates a Redis failure.
 */
@Slf4j
@Component
public class RedisGateway {

    private final StringRedisTemplate template; // null when redis disabled
    private final MeterRegistry meters;
    private final AtomicBoolean loggedDown = new AtomicBoolean(false);

    public RedisGateway(ObjectProvider<StringRedisTemplate> templateProvider, MeterRegistry meters) {
        this.template = templateProvider.getIfAvailable();
        this.meters = meters;
    }

    public boolean enabled() {
        return template != null;
    }

    public <T> Optional<T> call(Function<StringRedisTemplate, T> fn, String op) {
        if (template == null) {
            return Optional.empty();
        }
        try {
            T result = fn.apply(template);
            if (loggedDown.compareAndSet(true, false)) {
                log.info("Redis recovered");
            }
            return Optional.ofNullable(result);
        } catch (Exception e) {
            meters.counter("sentinel.redis.failures", "op", op).increment();
            if (loggedDown.compareAndSet(false, true)) {
                log.warn("Redis unavailable ({}), using in-memory fallback: {}", op, e.toString());
            }
            return Optional.empty();
        }
    }

    public <T> Optional<T> eval(RedisScript<T> script, List<String> keys, Object... args) {
        return call(t -> t.execute(script, keys, args), "eval");
    }
}
