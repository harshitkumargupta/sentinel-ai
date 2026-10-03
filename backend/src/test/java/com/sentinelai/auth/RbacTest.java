package com.sentinelai.auth;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentFeedback;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-based access control across the protected endpoint groups.
 */
class RbacTest extends IntegrationTestSupport {

    private Long createIncident() {
        return incidentRepository.save(Incident.builder()
                .org(organizationRepository.findById(ORG_ID).orElseThrow())
                .title("Test incident")
                .status(IncidentStatus.OPEN)
                .severity(Severity.HIGH)
                .feedback(IncidentFeedback.UNREVIEWED)
                .build()).getId();
    }

    @Test
    void viewerCannotChangeIncidentStatus() throws Exception {
        String viewer = bearerFor("viewer1", Role.VIEWER);
        Long incidentId = createIncident();

        mockMvc.perform(patch("/api/incidents/{id}/status", incidentId)
                        .header("Authorization", viewer)
                        .contentType("application/json")
                        .content("{\"status\":\"INVESTIGATING\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void analystCanChangeIncidentStatus() throws Exception {
        String analyst = bearerFor("analyst1", Role.ANALYST);
        Long incidentId = createIncident();

        mockMvc.perform(patch("/api/incidents/{id}/status", incidentId)
                        .header("Authorization", analyst)
                        .contentType("application/json")
                        .content("{\"status\":\"INVESTIGATING\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminCannotCreateUsers() throws Exception {
        String analyst = bearerFor("analyst2", Role.ANALYST);

        mockMvc.perform(post("/api/users")
                        .header("Authorization", analyst)
                        .contentType("application/json")
                        .content("{\"username\":\"x\",\"email\":\"x@sentinel.ai\",\"password\":\"Password@123\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void analystCannotCreateRules() throws Exception {
        String analyst = bearerFor("analyst3", Role.ANALYST);

        mockMvc.perform(post("/api/rules")
                        .header("Authorization", analyst)
                        .contentType("application/json")
                        .content("{\"name\":\"r1\",\"ruleType\":\"THRESHOLD\",\"severity\":\"LOW\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotIngestEvents() throws Exception {
        String viewer = bearerFor("viewer2", Role.VIEWER);

        mockMvc.perform(post("/api/events")
                        .header("Authorization", viewer)
                        .contentType("application/json")
                        .content("{\"eventType\":\"OTHER\",\"severity\":\"LOW\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminCannotReadAuditLogs() throws Exception {
        String analyst = bearerFor("analyst4", Role.ANALYST);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/audit-logs").header("Authorization", analyst))
                .andExpect(status().isForbidden());
    }
}
