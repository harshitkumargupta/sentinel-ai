package com.sentinelai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the SentinelAI modular monolith.
 *
 * <p>The application is organised into feature modules under {@code com.sentinelai}:
 * auth, event, incident, detection, risk, ai, dashboard, audit, and a shared common module.
 * Each module owns its own controllers, services, and persistence, communicating through
 * well-defined interfaces so that modules can later be split into services if needed.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SentinelAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelAiApplication.class, args);
    }
}
