package com.sentinelai.detection.engine;

import com.sentinelai.event.domain.EventType;
import com.sentinelai.redis.RedisProperties;
import com.sentinelai.redis.RedisTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** When Redis is down, RedisWindowStore transparently uses the in-memory fallback. */
class RedisWindowStoreFallbackTest {

    @Test
    void fallsBackToInMemoryWhenRedisDown() {
        RedisWindowStore store = new RedisWindowStore(
                RedisTestSupport.disabledGateway(), new RedisProperties(), new InMemoryWindowStore());
        String key = WindowKeys.of(EventType.FAILED_LOGIN, GroupBy.USERNAME, "bob");
        Instant now = Instant.now();
        for (int i = 0; i < 4; i++) {
            store.record(key, now.minusSeconds(i));
        }
        assertThat(store.count(key, now.minusSeconds(300), now)).isEqualTo(4);
    }
}
