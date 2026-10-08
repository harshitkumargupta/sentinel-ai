package com.sentinelai.demo;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Outside the demo/dev profiles: no Demo Center endpoints and no credentials on the login page. */
class DemoDisabledTest extends IntegrationTestSupport {

    @Test
    void demoInfoRevealsNothingAndEndpointsAreAbsent() throws Exception {
        mockMvc.perform(get("/api/public/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.demoMode").value(false))
                .andExpect(jsonPath("$.data.quickLogins.length()").value(0));
        String admin = bearerFor("nodemo-admin", Role.ADMIN);
        mockMvc.perform(post("/api/demo/reset").header("Authorization", admin))
                .andExpect(status().isNotFound());
    }
}
