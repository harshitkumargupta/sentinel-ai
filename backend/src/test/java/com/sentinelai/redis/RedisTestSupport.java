package com.sentinelai.redis;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Helpers for testing the in-memory fallback path (Redis absent/down). */
public final class RedisTestSupport {

    private RedisTestSupport() {
    }

    /** A gateway with no template — every call returns empty, exercising the fallback. */
    public static RedisGateway disabledGateway() {
        ObjectProvider<StringRedisTemplate> none = new ObjectProvider<>() {
            public StringRedisTemplate getObject() {
                return null;
            }

            public StringRedisTemplate getObject(Object... args) {
                return null;
            }

            public StringRedisTemplate getIfAvailable() {
                return null;
            }

            public StringRedisTemplate getIfUnique() {
                return null;
            }
        };
        return new RedisGateway(none, new SimpleMeterRegistry());
    }
}
