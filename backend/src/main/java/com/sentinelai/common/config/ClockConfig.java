package com.sentinelai.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides an injectable {@link Clock} so time-dependent logic (detection windows, lockout,
 * token expiry) stays deterministic and testable.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
