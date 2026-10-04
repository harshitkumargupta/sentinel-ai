package com.sentinelai.ratelimit;

import com.sentinelai.redis.RedisTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** In-memory fallback behaviour (Redis down): burst exhaustion, per-key isolation. */
class TokenBucketRateLimiterTest {

    private final RateLimiter limiter = new TokenBucketRateLimiter(RedisTestSupport.disabledGateway());

    @Test
    void allowsBurstThenDenies() {
        int capacity = 3;
        for (int i = 0; i < capacity; i++) {
            assertThat(limiter.tryAcquire("k1", capacity, 0.001).allowed()).isTrue();
        }
        var denied = limiter.tryAcquire("k1", capacity, 0.001);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isGreaterThan(0);
        assertThat(denied.remaining()).isZero();
    }

    @Test
    void keysAreIsolated() {
        int capacity = 1;
        assertThat(limiter.tryAcquire("a", capacity, 0.001).allowed()).isTrue();
        assertThat(limiter.tryAcquire("a", capacity, 0.001).allowed()).isFalse();
        // Different key has its own bucket.
        assertThat(limiter.tryAcquire("b", capacity, 0.001).allowed()).isTrue();
    }
}
