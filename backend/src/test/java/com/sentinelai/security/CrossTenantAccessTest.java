package com.sentinelai.security;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentFeedback;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant isolation: a user in org B cannot read an org A resource by guessing its id. The by-id
 * lookups filter on the actor's org and return 404 (not 403) so existence isn't leaked.
 */
class CrossTenantAccessTest extends IntegrationTestSupport {

    @Autowired
    private IncidentRepository incidentRepository;
    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    void userCannotReadAnotherOrgsIncident() throws Exception {
        // org A (the seeded default org) owns an incident.
        Organization orgA = organizationRepository.findById(ORG_ID).orElseThrow();
        Long incidentId = incidentRepository.save(Incident.builder()
                .org(orgA).title("org A incident").status(IncidentStatus.OPEN)
                .severity(Severity.HIGH).feedback(IncidentFeedback.UNREVIEWED).build()).getId();

        // org B has its own admin.
        Organization orgB = organizationRepository.save(
                Organization.builder().name("tenant-b-" + System.nanoTime()).build());
        userRepository.save(User.builder().org(orgB).username("tenantB-admin")
                .email("tenantB-admin@sentinel.ai").passwordHash(passwordEncoder.encode(PASSWORD))
                .role(Role.ADMIN).enabled(true).build());
        String orgBToken = "Bearer " + loginAndGetToken("tenantB-admin");

        // org B admin cannot see org A's incident -> 404.
        mockMvc.perform(get("/api/incidents/" + incidentId).header("Authorization", orgBToken))
                .andExpect(status().isNotFound());

        // Control: an org A admin can.
        String orgAToken = bearerFor("tenantA-admin", Role.ADMIN);
        mockMvc.perform(get("/api/incidents/" + incidentId).header("Authorization", orgAToken))
                .andExpect(status().isOk());
    }
}
