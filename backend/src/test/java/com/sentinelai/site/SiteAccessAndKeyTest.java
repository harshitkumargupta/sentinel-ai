package com.sentinelai.site;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.site.domain.Site;
import com.sentinelai.site.domain.SiteStatus;
import com.sentinelai.site.domain.UserSiteAccess;
import com.sentinelai.site.repository.SiteRepository;
import com.sentinelai.site.repository.UserSiteAccessRepository;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SiteAccessAndKeyTest extends IntegrationTestSupport {

    @Autowired private SiteService siteService;
    @Autowired private SiteRepository siteRepository;
    @Autowired private UserSiteAccessRepository userSiteAccessRepository;

    @Test
    void scopedAccessIsolatesSitesBetweenUsers() {
        Site s1 = siteRepository.save(Site.builder().org(organizationRepository.findById(ORG_ID).orElseThrow())
                .name("S1").status(SiteStatus.ACTIVE).build());
        Site s2 = siteRepository.save(Site.builder().org(organizationRepository.findById(ORG_ID).orElseThrow())
                .name("S2").status(SiteStatus.ACTIVE).build());

        User viewer = createUser("siteviewer", Role.VIEWER);
        userSiteAccessRepository.save(new UserSiteAccess(viewer.getId(), s1.getId()));
        AppUserPrincipal principal = new AppUserPrincipal(viewer);

        assertThat(siteService.hasAccess(principal, s1.getId())).isTrue();
        assertThat(siteService.hasAccess(principal, s2.getId())).isFalse();
        assertThat(siteService.listAccessible(principal)).extracting("id").contains(s1.getId()).doesNotContain(s2.getId());
    }

    @Test
    void revokedApiKeyIsRejectedOnIngest() throws Exception {
        String admin = bearerFor("sitekadmin", Role.ADMIN);

        MvcResult created = mockMvc.perform(post("/api/sites").header("Authorization", admin)
                        .contentType("application/json").content("{\"name\":\"KeySite\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        String apiKey = body.path("apiKey").path("apiKey").asText();
        long keyId = body.path("apiKey").path("keyId").asLong();

        String ingestBody = "{\"payload\":{\"eventType\":\"OTHER\",\"severity\":\"LOW\",\"sourceIp\":\"203.0.113.5\"}}";

        // Valid key ingests.
        mockMvc.perform(post("/api/events/ingest").header("X-API-Key", apiKey)
                        .contentType("application/json").content(ingestBody))
                .andExpect(status().isOk());

        // Revoke, then the same key is rejected.
        mockMvc.perform(delete("/api/sites/keys/{id}", keyId).header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/events/ingest").header("X-API-Key", apiKey)
                        .contentType("application/json").content(ingestBody))
                .andExpect(status().isUnauthorized());
    }
}
