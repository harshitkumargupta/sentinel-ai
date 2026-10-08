package com.sentinelai.detection.rule;

import com.sentinelai.alert.domain.Alert;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Behaviour rules learn each user's baseline from stored events and flag deviations. */
class UbaRulesTest extends IntegrationTestSupport {

    private static final Instant DAY0 = Instant.parse("2026-05-01T00:00:00Z");

    @Autowired private IngestionService ingestionService;
    @Autowired private DetectionRuleRepository ruleRepository;
    @Autowired private AlertRepository alertRepository;

    @BeforeEach
    void rules() {
        alertRepository.deleteAll();
        ruleRepository.findAll().stream().filter(r -> r.getRuleType().startsWith("UBA_") || r.getName().startsWith("uba-"))
                .forEach(ruleRepository::delete);
        var org = organizationRepository.findById(ORG_ID).orElseThrow();
        save(org, "uba-hour", "UBA_UNUSUAL_HOUR", "{\"minSamples\":10,\"lookbackDays\":30,\"toleranceHours\":1,\"maxSharePercent\":5}", "T1078");
        save(org, "uba-loc", "UBA_NEW_LOCATION", "{\"minSamples\":5,\"lookbackDays\":30}", "T1078");
        save(org, "uba-spike", "UBA_FAILED_SPIKE", "{\"windowSeconds\":3600,\"lookbackDays\":14,\"minCount\":5,\"zThreshold\":3}", "T1110");
    }

    private void save(com.sentinelai.common.domain.Organization org, String name, String type, String config, String mitre) {
        ruleRepository.save(DetectionRule.builder().org(org).name(name).ruleType(type).config(config)
                .enabled(true).severity(Severity.MEDIUM).mitreTechnique(mitre).version(1).build());
    }

    private void event(String type, String user, String ip, String country, Instant at) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("eventType", type);
        p.put("severity", "LOW");
        p.put("username", user);
        p.put("sourceIp", ip);
        p.put("geoCountry", country);
        p.put("eventTimestamp", at.toString());
        ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
    }

    private List<Alert> alerts(String ruleType, String user) {
        return alertRepository.findAll().stream()
                .filter(a -> a.getRuleType().equals(ruleType) && a.getMessage().contains(user)).toList();
    }

    @Test
    void unusualHourAndNewLocationAgainstLearnedBaseline() {
        for (int d = 0; d < 12; d++) { // office-hours habit from one network in one country
            event("LOGIN_SUCCESS", "uba-alice", "10.50.1." + (10 + d), "US", DAY0.plus(d, ChronoUnit.DAYS).plus(9, ChronoUnit.HOURS));
        }
        assertThat(alerts("UBA_UNUSUAL_HOUR", "uba-alice")).isEmpty();
        assertThat(alerts("UBA_NEW_LOCATION", "uba-alice")).isEmpty();

        event("LOGIN_SUCCESS", "uba-alice", "10.50.1.99", "US", DAY0.plus(13, ChronoUnit.DAYS).plus(10, ChronoUnit.HOURS));
        assertThat(alerts("UBA_UNUSUAL_HOUR", "uba-alice")).isEmpty(); // within ±1h of habit
        assertThat(alerts("UBA_NEW_LOCATION", "uba-alice")).isEmpty(); // known /24 and country

        event("LOGIN_SUCCESS", "uba-alice", "185.220.9.9", "NL", DAY0.plus(14, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS));
        assertThat(alerts("UBA_UNUSUAL_HOUR", "uba-alice")).singleElement()
                .satisfies(a -> assertThat(a.getMessage()).contains("03:00 UTC").contains("13 prior logins"));
        assertThat(alerts("UBA_NEW_LOCATION", "uba-alice")).singleElement()
                .satisfies(a -> assertThat(a.getMessage()).contains("country NL").contains("network 185.220.9.0/24"));
    }

    @Test
    void coldStartUsersAreNotFlagged() {
        event("LOGIN_SUCCESS", "uba-new", "8.8.4.4", "BR", DAY0.plus(3, ChronoUnit.HOURS));
        assertThat(alerts("UBA_UNUSUAL_HOUR", "uba-new")).isEmpty();
        assertThat(alerts("UBA_NEW_LOCATION", "uba-new")).isEmpty();
    }

    @Test
    void failedLoginSpikeVersusOwnBaselineFiresOnce() {
        for (int d = 0; d < 10; d++) { // a typo now and then
            event("FAILED_LOGIN", "uba-bob", "10.50.2.2", "US", DAY0.plus(d, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS));
        }
        assertThat(alerts("UBA_FAILED_SPIKE", "uba-bob")).isEmpty();
        Instant spike = DAY0.plus(12, ChronoUnit.DAYS).plus(14, ChronoUnit.HOURS);
        for (int i = 0; i < 9; i++) {
            event("FAILED_LOGIN", "uba-bob", "10.50.2.2", "US", spike.plusSeconds(i * 60L));
        }
        assertThat(alerts("UBA_FAILED_SPIKE", "uba-bob")).singleElement()
                .satisfies(a -> assertThat(a.getMitreTechnique()).isEqualTo("T1110"));
    }
}
