package com.sentinelai.security;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-based access control: lower-privileged roles cannot reach higher-privileged operations.
 * These use no-body ADMIN-only endpoints so the 403 comes from authorization, not body validation.
 */
class PrivilegeEscalationTest extends IntegrationTestSupport {

    @Test
    void viewerCannotListUsers() throws Exception {
        String viewer = bearerFor("viewer1", Role.VIEWER);
        mockMvc.perform(get("/api/users").header("Authorization", viewer))
                .andExpect(status().isForbidden());
    }

    @Test
    void analystCannotListUsers() throws Exception {
        String analyst = bearerFor("analyst1", Role.ANALYST);
        mockMvc.perform(get("/api/users").header("Authorization", analyst))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotDeleteRule() throws Exception {
        String viewer = bearerFor("viewer2", Role.VIEWER);
        mockMvc.perform(delete("/api/rules/1").header("Authorization", viewer))
                .andExpect(status().isForbidden());
    }

    @Test
    void analystCannotDeleteRule() throws Exception {
        String analyst = bearerFor("analyst2", Role.ANALYST);
        mockMvc.perform(delete("/api/rules/1").header("Authorization", analyst))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminIsAllowedToListUsers() throws Exception {
        String admin = bearerFor("admin1", Role.ADMIN);
        mockMvc.perform(get("/api/users").header("Authorization", admin))
                .andExpect(status().isOk());
    }
}
