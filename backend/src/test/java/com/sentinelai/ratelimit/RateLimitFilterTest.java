package com.sentinelai.ratelimit;

import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login limiter returns 429 with Retry-After + X-RateLimit headers once the burst is spent. */
@SpringBootTest(properties = {
        "sentinel.rate-limit.enabled=true",
        "sentinel.rate-limit.login.capacity=2",
        "sentinel.rate-limit.login.refill-per-second=0.0001"
})
class RateLimitFilterTest extends IntegrationTestSupport {

    @Test
    void loginIsRateLimitedWithHeaders() throws Exception {
        String body = "{\"username\":\"nobody\",\"password\":\"x\"}";
        // capacity 2 -> first two pass the limiter (then 401 bad creds), third is 429.
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body));
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body));
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().is(429))
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().exists("X-RateLimit-Remaining"));
    }
}
