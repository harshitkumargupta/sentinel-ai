package com.sentinelai.ratelimit;

/** Token-bucket rate limiter. Implementations must never throw — fall back locally if Redis is down. */
public interface RateLimiter {

    Result tryAcquire(String key, int capacity, double refillPerSecond);

    record Result(boolean allowed, long remaining, int limit, long retryAfterSeconds) {
    }
}
