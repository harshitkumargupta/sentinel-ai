package com.sentinelai.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.ai.repository.AiAnalysisRepository;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentEvent;
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.notification.repository.NotificationRepository;
import com.sentinelai.playbook.repository.PlaybookActionRepository;
import com.sentinelai.simulator.scenario.PromptInjectionScenario;
import com.sentinelai.ingestion.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared seeding and helpers for AI pipeline tests. Concrete subclasses declare their own
 * {@code @SpringBootTest}/{@code @ActiveProfiles} so they can toggle {@code sentinel.ai.enabled}.
 */
abstract class AiTestSupport {

    protected static final Long ORG_ID = 1L;
    protected static final Instant BASE = Instant.parse("2026-04-01T00:00:00Z");

    @Autowired protected OrganizationRepository organizationRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected DetectionRuleRepository ruleRepository;
    @Autowired protected IngestionService ingestionService;
    @Autowired protected IncidentRepository incidentRepository;
    @Autowired protected IncidentEventRepository incidentEventRepository;
    @Autowired protected AlertRepository alertRepository;
    @Autowired protected SecurityEventRepository securityEventRepository;
    @Autowired protected AiAnalysisRepository aiAnalysisRepository;
    @Autowired protected PlaybookActionRepository playbookActionRepository;
    @Autowired protected NotificationRepository notificationRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected ObjectMapper objectMapper;

    protected User admin;

    @BeforeEach
    void seed() {
        playbookActionRepository.deleteAll();
        aiAnalysisRepository.deleteAll();
        notificationRepository.deleteAll();
        incidentRepository.deleteAll();
        alertRepository.deleteAll();
        securityEventRepository.deleteAll();
        ruleRepository.deleteAll();
        userRepository.deleteAll();

        Organization org = organizationRepository.findById(ORG_ID).orElseThrow();
        ruleRepository.save(DetectionRule.builder().org(org).name("bf").ruleType("BRUTE_FORCE")
                .config("{\"threshold\":3,\"windowSeconds\":3600,\"groupBy\":\"username\"}")
                .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());
        admin = userRepository.save(User.builder().org(org).username("ai-admin")
                .email("ai-admin@sentinel.ai").passwordHash(passwordEncoder.encode("x"))
                .role(Role.ADMIN).enabled(true).build());
    }

    protected AppUserPrincipal principal() {
        return new AppUserPrincipal(admin);
    }

    /** Ingest enough failed logins to raise a brute-force incident for one user; returns it. */
    protected Incident bruteForceIncident(String user) {
        for (int i = 0; i < 4; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("eventType", "FAILED_LOGIN");
            p.put("severity", "LOW");
            p.put("username", user);
            p.put("sourceIp", "203.0.113.5");
            p.put("eventTimestamp", BASE.plusSeconds(i * 10L).toString());
            ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
        }
        return incidentRepository.findAll().stream()
                .filter(i -> ("user:" + user).equals(i.getCorrelationKey()))
                .findFirst().orElseThrow();
    }

    /** Link an event whose user agent carries a prompt-injection string to the incident. */
    protected void addInjectionEvent(Incident incident) {
        SecurityEvent ev = securityEventRepository.save(SecurityEvent.builder()
                .org(organizationRepository.getReferenceById(ORG_ID))
                .eventType(EventType.SUSPICIOUS_LOGIN)
                .severity(Severity.MEDIUM)
                .sourceIp("203.0.113.5")
                .username("mallory")
                .userAgent(PromptInjectionScenario.INJECTION)
                .entityKey(incident.getCorrelationKey())
                .eventTimestamp(BASE.plusSeconds(100))
                .build());
        incidentEventRepository.save(new IncidentEvent(incident, ev));
    }
}
