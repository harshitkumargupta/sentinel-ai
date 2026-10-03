package com.sentinelai.common.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test — no Spring context or database required, so it runs in CI
 * without a MySQL instance.
 */
class HealthControllerTest {

    @Test
    void healthReportsUp() {
        Map<String, Object> body = new HealthController().health();

        assertThat(body).containsEntry("status", "UP");
        assertThat(body).containsEntry("service", "sentinel-ai");
        assertThat(body).containsKey("timestamp");
    }
}
