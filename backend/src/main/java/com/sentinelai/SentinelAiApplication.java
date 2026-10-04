package com.sentinelai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the SentinelAI modular monolith.
 *
 * <p>The application is organised into feature modules under {@code com.sentinelai}:
 * auth, event, incident, detection, risk, ai, dashboard, audit, and a shared common module.
 * Each module owns its own controllers, services, and persistence, communicating through
 * well-defined interfaces so that modules can later be split into services if needed.
 */
// Redis is wired explicitly via RedisConfig behind sentinel.redis.enabled, so the default
// auto-configuration (which would always create a localhost connection) is excluded.
@SpringBootApplication(exclude = {RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})
@ConfigurationPropertiesScan
@EnableScheduling
public class SentinelAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelAiApplication.class, args);
    }
}
