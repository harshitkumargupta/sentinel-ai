package com.sentinelai.common.bootstrap;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.honeytoken.domain.Honeytoken;
import com.sentinelai.honeytoken.repository.HoneytokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Seeds dev-only data: one user per role, three detection rules (with MITRE technique IDs),
 * and two hashed honeytokens — all under the "Default Org" (id 1) created by migration V2.
 *
 * <p>Active only under the {@code dev} profile and idempotent, so it is safe on every startup
 * and never touches test or prod databases. Dev passwords are documented in the README.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final DetectionRuleRepository detectionRuleRepository;
    private final HoneytokenRepository honeytokenRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(String... args) {
        Organization org = organizationRepository.findById(1L).orElse(null);
        if (org == null) {
            log.warn("Default Org (id 1) not found — skipping dev seed.");
            return;
        }
        seedUsers(org);
        seedDetectionRules(org);
        seedHoneytokens(org);
    }

    private void seedUsers(Organization org) {
        if (userRepository.count() > 0) {
            log.debug("Users already present — skipping user seed.");
            return;
        }
        userRepository.save(buildUser(org, "admin", "admin@sentinel.ai", "Admin@123", Role.ADMIN));
        userRepository.save(buildUser(org, "analyst", "analyst@sentinel.ai", "Analyst@123", Role.ANALYST));
        userRepository.save(buildUser(org, "viewer", "viewer@sentinel.ai", "Viewer@123", Role.VIEWER));
        log.info("Seeded 3 dev users (admin/analyst/viewer).");
    }

    private User buildUser(Organization org, String username, String email, String rawPassword, Role role) {
        return User.builder()
                .org(org)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .enabled(true)
                .build();
    }

    private void seedDetectionRules(Organization org) {
        if (detectionRuleRepository.count() > 0) {
            log.debug("Detection rules already present — skipping rule seed.");
            return;
        }
        User admin = userRepository.findByUsername("admin").orElse(null);

        seedRule(org, admin, "Brute force", "BRUTE_FORCE",
                "{\"threshold\":10,\"windowSeconds\":300,\"groupBy\":\"username\"}",
                Severity.HIGH, "T1110");
        seedRule(org, admin, "Credential stuffing", "CREDENTIAL_STUFFING",
                "{\"distinctUsers\":5,\"windowSeconds\":300}", Severity.HIGH, "T1110.004");
        seedRule(org, admin, "High-frequency API", "HIGH_FREQUENCY_API",
                "{\"threshold\":100,\"windowSeconds\":60,\"groupBy\":\"sourceIp\"}",
                Severity.MEDIUM, "T1499");
        seedRule(org, admin, "Suspicious login", "SUSPICIOUS_LOGIN",
                "{\"oddHourStart\":0,\"oddHourEnd\":5}", Severity.MEDIUM, "T1078");
        seedRule(org, admin, "Impossible travel", "IMPOSSIBLE_TRAVEL",
                "{\"minSecondsBetweenCountries\":3600}", Severity.HIGH, "T1078");
        seedRule(org, admin, "Abnormal access", "ABNORMAL_ACCESS",
                "{}", Severity.HIGH, "T1548");
        seedRule(org, admin, "Honeytoken access", "HONEYTOKEN",
                "{}", Severity.CRITICAL, "T1078.001");

        log.info("Seeded 7 detection rules.");
    }

    private void seedRule(Organization org, User admin, String name, String ruleType,
                          String config, Severity severity, String mitre) {
        detectionRuleRepository.save(DetectionRule.builder()
                .org(org)
                .name(name)
                .description(name + " detection rule (" + ruleType + ")")
                .ruleType(ruleType)
                .config(config)
                .enabled(true)
                .severity(severity)
                .mitreTechnique(mitre)
                .version(1)
                .createdBy(admin)
                .build());
    }

    private void seedHoneytokens(Organization org) {
        if (honeytokenRepository.count() > 0) {
            log.debug("Honeytokens already present — skipping honeytoken seed.");
            return;
        }
        honeytokenRepository.save(Honeytoken.builder()
                .org(org)
                .type("AWS_ACCESS_KEY")
                .valueHash(sha256("AKIA-DECOY-EXAMPLE-0001"))
                .description("Decoy AWS access key planted in a fake config file.")
                .triggeredCount(0)
                .build());

        honeytokenRepository.save(Honeytoken.builder()
                .org(org)
                .type("DB_CREDENTIAL")
                .valueHash(sha256("decoy-db-password-0002"))
                .description("Decoy database credential referenced by a honeypot service.")
                .triggeredCount(0)
                .build());

        log.info("Seeded 2 hashed honeytokens.");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
