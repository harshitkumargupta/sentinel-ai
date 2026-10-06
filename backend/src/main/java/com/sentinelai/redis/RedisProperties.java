package com.sentinelai.redis;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Redis connection + TTL config. Disabled by default — everything falls back to in-memory. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.redis")
public class RedisProperties {

    private boolean enabled = false;
    private String host = "localhost";
    @Min(1)
    private int port = 6379;
    @Min(10)
    private int timeoutMs = 200;

    /** TTLs (seconds). */
    @Min(1)
    private long cacheTtlSeconds = 30;
    @Min(1)
    private long windowTtlSeconds = 86_400;
    @Min(1)
    private long rateLimitTtlSeconds = 3_600;
}
