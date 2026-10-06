package com.sentinelai.security;

import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms the security response headers (clickjacking, sniffing, CSP, referrer, permissions) are
 * present on responses, and that HSTS is absent in the test/dev profile (plain HTTP).
 */
class SecurityHeadersTest extends IntegrationTestSupport {

    @Test
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("Permissions-Policy"))
                // HSTS only in prod (HTTPS); must not be sent on plain HTTP in dev/test.
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}
