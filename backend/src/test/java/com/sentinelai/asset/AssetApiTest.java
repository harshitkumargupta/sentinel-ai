package com.sentinelai.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Asset CRUD + import, ingest-time linking, and asset criticality feeding risk and magnitude. */
class AssetApiTest extends IntegrationTestSupport {

    private static final AtomicLong CLOCK = new AtomicLong(Instant.parse("2026-10-02T00:00:00Z").getEpochSecond());

    @Autowired private AssetRepository assetRepository;
    @Autowired private IngestionService ingestionService;
    @Autowired private DetectionRuleRepository ruleRepository;

    private String analyst;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        assetRepository.deleteAll();
        if (!ruleRepository.existsByName("asset-bf")) {
            ruleRepository.save(DetectionRule.builder().org(organizationRepository.findById(ORG_ID).orElseThrow())
                    .name("asset-bf").ruleType("BRUTE_FORCE").config("{\"threshold\":3,\"windowSeconds\":3600}")
                    .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());
        }
        analyst = bearerFor("asset-analyst", Role.ANALYST);
        admin = bearerFor("asset-admin", Role.ADMIN);
    }

    private JsonNode create(String token, String json, int expected) throws Exception {
        String res = mockMvc.perform(post("/api/assets").header("Authorization", token).contentType("application/json").content(json))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).path("data");
    }

    private Incident attack(String user, String host) {
        long t0 = CLOCK.addAndGet(10_000);
        for (int i = 0; i < 4; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("eventType", "FAILED_LOGIN");
            p.put("severity", "LOW");
            p.put("username", user);
            p.put("sourceIp", "45.33.4.4");
            p.put("entityKey", "host:" + host);
            p.put("eventTimestamp", Instant.ofEpochSecond(t0 + i).toString());
            ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
        }
        return incidentRepository.findAll().stream().filter(i -> ("user:" + user).equals(i.getCorrelationKey()))
                .findFirst().orElseThrow();
    }

    @Test
    void crudValidationAndRbac() throws Exception {
        String ok = "{\"hostname\":\"db-prod-1\",\"ip\":\"10.30.0.5\",\"owner\":\"Data\",\"type\":\"SERVER\",\"environment\":\"PRODUCTION\",\"criticality\":\"CRITICAL\"}";
        long id = create(analyst, ok, 200).path("id").asLong();
        create(analyst, ok, 409);
        create(analyst, "{\"type\":\"SERVER\",\"environment\":\"PRODUCTION\",\"criticality\":\"LOW\"}", 400);
        create(analyst, ok.replace("db-prod-1", "x2").replace("10.30.0.5", "10.30.0.999"), 400);
        create(analyst, ok.replace("db-prod-1", "bad host!"), 400);
        String viewer = bearerFor("asset-viewer", Role.VIEWER);
        create(viewer, ok.replace("db-prod-1", "v1").replace("10.30.0.5", "10.30.0.6"), 403);
        mockMvc.perform(get("/api/assets").header("Authorization", viewer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].hostname").value("db-prod-1"));
        mockMvc.perform(delete("/api/assets/" + id).header("Authorization", analyst)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/assets/" + id).header("Authorization", admin)).andExpect(status().isOk());
    }

    @Test
    void criticalAssetRaisesRiskAndMagnitudeWithNamedBreakdown() throws Exception {
        Incident unknownHost = attack("asset-u1", "no-such-host");
        create(analyst, "{\"hostname\":\"crown-jewel\",\"type\":\"SERVER\",\"environment\":\"PRODUCTION\",\"criticality\":\"CRITICAL\"}", 200);
        Incident onAsset = attack("asset-u2", "crown-jewel");

        assertThat(onAsset.getRiskScore()).isGreaterThan(unknownHost.getRiskScore());
        mockMvc.perform(get("/api/incidents/" + onAsset.getId() + "/risk").header("Authorization", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.breakdown[?(@.name=='asset_criticality')].points").value(32))
                .andExpect(jsonPath("$.data.breakdown[?(@.name=='asset_criticality')].reason")
                        .value("Max asset criticality 4: crown-jewel (CRITICAL)"));
        mockMvc.perform(get("/api/offenses/" + onAsset.getId() + "/magnitude").header("Authorization", analyst))
                .andExpect(jsonPath("$.data.relevanceReasons").value(org.hamcrest.Matchers.hasItem("+4 asset criticality")));
    }

    @Test
    void relinkAndCsvImport() throws Exception {
        attack("asset-u3", "late-host"); // events arrive before the asset is inventoried
        String csv = "hostname,ip,owner,type,environment,criticality,description\n"
                + "late-host,10.30.1.1,Ops,SERVER,PRODUCTION,HIGH,added later\n"
                + "ws-1,10.30.1.2,Fin,WORKSTATION,CORPORATE,MEDIUM,\n"
                + "ws-2,10.30.1.2,Fin,WORKSTATION,CORPORATE,LOW,ip clash\n"
                + "ws-3,,Fin,WORKSTATION,CORPORATE,EXTREME,bad criticality\n"
                + ",,,,,,\n";
        String res = mockMvc.perform(multipart("/api/assets/import")
                        .file(new MockMultipartFile("file", "assets.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", analyst))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode r = objectMapper.readTree(res).path("data");
        assertThat(r.path("created").asInt()).isEqualTo(2);
        assertThat(r.path("skipped").asInt()).isEqualTo(1);
        assertThat(r.path("errors").toString()).contains("row 4").contains("row 5");
        assertThat(r.path("relinkedEvents").asInt()).isEqualTo(4);

        long id = assetRepository.findByOrg_IdAndHostnameIgnoreCase(ORG_ID, "late-host").orElseThrow().getId();
        mockMvc.perform(get("/api/assets/" + id).header("Authorization", analyst))
                .andExpect(jsonPath("$.data.asset.linkedEvents").value(4))
                .andExpect(jsonPath("$.data.incidents.length()").value(1));

        mockMvc.perform(multipart("/api/assets/import")
                        .file(new MockMultipartFile("file", "assets.exe", "application/octet-stream", "x".getBytes()))
                        .header("Authorization", analyst))
                .andExpect(status().isBadRequest());
    }
}
