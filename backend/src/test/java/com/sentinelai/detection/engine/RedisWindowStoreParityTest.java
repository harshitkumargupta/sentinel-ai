package com.sentinelai.detection.engine;

import com.sentinelai.event.domain.EventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedisWindowStore and InMemoryWindowStore must make identical counting decisions for identical
 * inputs. Requires a local Redis (README: move to Testcontainers once Docker is available).
 */
@SpringBootTest(properties = "sentinel.redis.enabled=true")
@ActiveProfiles("test")
class RedisWindowStoreParityTest {

    @Autowired
    private WindowStore windowStore; // RedisWindowStore (primary when redis enabled)

    @Test
    void redisAndInMemoryAgree() {
        InMemoryWindowStore inMemory = new InMemoryWindowStore();
        String key = WindowKeys.of(EventType.FAILED_LOGIN, GroupBy.USERNAME, "parity-" + UUID.randomUUID());
        Instant now = Instant.now();

        for (int i = 0; i < 7; i++) {
            Instant ts = now.minusSeconds(i * 10L);
            windowStore.record(key, ts);
            inMemory.record(key, ts);
        }

        Instant from = now.minusSeconds(300);
        assertThat(windowStore.count(key, from, now)).isEqualTo(inMemory.count(key, from, now));
        assertThat(windowStore.count(key, from, now)).isEqualTo(7);

        Instant narrow = now.minusSeconds(25);
        assertThat(windowStore.count(key, narrow, now)).isEqualTo(inMemory.count(key, narrow, now));
    }
}
