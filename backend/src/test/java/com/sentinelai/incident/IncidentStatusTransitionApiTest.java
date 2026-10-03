package com.sentinelai.incident;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentFeedback;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidentStatusTransitionApiTest extends IntegrationTestSupport {

    private Long createIncident() {
        return incidentRepository.save(Incident.builder()
                .org(organizationRepository.findById(ORG_ID).orElseThrow())
                .title("Transition test")
                .status(IncidentStatus.OPEN)
                .severity(Severity.MEDIUM)
                .feedback(IncidentFeedback.UNREVIEWED)
                .build()).getId();
    }

    @Test
    void allowsValidTransition() throws Exception {
        String analyst = bearerFor("analystT1", Role.ANALYST);
        Long id = createIncident();

        mockMvc.perform(patch("/api/incidents/{id}/status", id)
                        .header("Authorization", analyst)
                        .contentType("application/json")
                        .content("{\"status\":\"INVESTIGATING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INVESTIGATING"));
    }

    @Test
    void rejectsIllegalTransition() throws Exception {
        String analyst = bearerFor("analystT2", Role.ANALYST);
        Long id = createIncident();

        // OPEN -> RESOLVED is not an allowed transition.
        mockMvc.perform(patch("/api/incidents/{id}/status", id)
                        .header("Authorization", analyst)
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }
}
