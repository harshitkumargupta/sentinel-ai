package com.sentinelai.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Lightweight liveness endpoint for the frontend and uptime checks.
 * Kubernetes / Actuator probes use {@code /actuator/health}; this is the app's own
 * stable, public contract at {@code /api/health}.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "sentinel-ai",
                "timestamp", Instant.now().toString()
        );
    }
}
