package com.sentinelai.common.bootstrap;

import com.sentinelai.asset.Asset;
import com.sentinelai.asset.AssetCriticality;
import com.sentinelai.asset.AssetEnvironment;
import com.sentinelai.asset.AssetRepository;
import com.sentinelai.asset.AssetType;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.adminrisk.domain.AdminBaseline;
import com.sentinelai.adminrisk.domain.AdminBaselineId;
import com.sentinelai.adminrisk.domain.AdminBaselineRepository;
import com.sentinelai.honeytoken.domain.Honeytoken;
import com.sentinelai.honeytoken.repository.HoneytokenRepository;
import com.sentinelai.site.domain.UserSiteAccess;
import com.sentinelai.site.repository.UserSiteAccessRepository;
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
 * Seeds dev/demo data: one user per role, the built-in detection rules (with MITRE technique IDs),
 * and two hashed honeytokens — all under the "Default Org" (id 1) created by migration V2.
 *
 * <p>Active only under the {@code dev} and {@code demo} profiles and idempotent per item (a user or
 * rule is created only if its name is missing), so it is safe on every startup, fills in rules added
 * by later releases, and never touches test or prod databases. Passwords are documented in the README.
 */
@Slf4j
@Component
@Profile({"dev", "demo"})
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final DetectionRuleRepository detectionRuleRepository;
    private final HoneytokenRepository honeytokenRepository;
    private final UserSiteAccessRepository userSiteAccessRepository;
    private final AdminBaselineRepository adminBaselineRepository;
    private final AssetRepository assetRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final Long DEFAULT_SITE_ID = 1L;

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
        seedSiteAccessAndBaselines();
        seedAssets(org);
    }

    /** Sample inventory (matches the bundled datasets' hosts/IPs) so asset criticality shows in risk. */
    private void seedAssets(Organization org) {
        if (assetRepository.count() > 0) {
            return;
        }
        seedAsset(org, "web-01", "10.20.0.15", "Platform team", AssetType.SERVER, AssetEnvironment.PRODUCTION, AssetCriticality.HIGH);
        seedAsset(org, "SRV-DB-02", "10.20.0.10", "Data team", AssetType.SERVER, AssetEnvironment.PRODUCTION, AssetCriticality.CRITICAL);
        seedAsset(org, "SRV-APP-11", "10.20.0.5", "Platform team", AssetType.SERVER, AssetEnvironment.PRODUCTION, AssetCriticality.HIGH);
        seedAsset(org, "WS-FIN-023", "10.20.3.23", "Finance", AssetType.WORKSTATION, AssetEnvironment.CORPORATE, AssetCriticality.MEDIUM);
        log.info("Seeded 4 sample assets.");
    }

    private void seedAsset(Organization org, String host, String ip, String owner, AssetType type,
                           AssetEnvironment env, AssetCriticality crit) {
        assetRepository.save(Asset.builder().org(org).hostname(host).ip(ip).owner(owner).type(type)
                .environment(env).criticality(crit).build());
    }

    private void seedSiteAccessAndBaselines() {
        // Dev users are created after V21 ran, so grant them default-site access here (idempotent).
        for (User u : userRepository.findAll()) {
            if (!userSiteAccessRepository.existsById_UserIdAndId_SiteId(u.getId(), DEFAULT_SITE_ID)) {
                userSiteAccessRepository.save(new UserSiteAccess(u.getId(), DEFAULT_SITE_ID));
            }
        }
        // Seed an admin baseline so the admin-risk guard has something to deviate from.
        userRepository.findByUsername("admin").ifPresent(admin -> {
            if (adminBaselineRepository.findById_UserId(admin.getId()).isEmpty()) {
                baseline(admin.getId(), "known_countries", "[\"US\"]");
                baseline(admin.getId(), "typical_hours", "[8,9,10,11,12,13,14,15,16,17,18,19]");
                baseline(admin.getId(), "known_sites", "[1]");
            }
        });
    }

    private void baseline(Long userId, String metric, String data) {
        adminBaselineRepository.save(AdminBaseline.builder()
                .id(new AdminBaselineId(userId, metric)).data(data).build());
    }

    private void seedUsers(Organization org) {
        int created = 0;
        created += seedUser(org, "admin", "admin@sentinel.ai", "Admin@123", Role.ADMIN);
        created += seedUser(org, "analyst", "analyst@sentinel.ai", "Analyst@123", Role.ANALYST);
        created += seedUser(org, "viewer", "viewer@sentinel.ai", "Viewer@123", Role.VIEWER);
        if (created > 0) {
            log.info("Seeded {} dev/demo user(s) (admin/analyst/viewer).", created);
        }
    }

    private int seedUser(Organization org, String username, String email, String rawPassword, Role role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return 0;
        }
        userRepository.save(buildUser(org, username, email, rawPassword, role));
        return 1;
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
        User admin = userRepository.findByUsername("admin").orElse(null);
        int before = (int) detectionRuleRepository.count();

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
        seedRule(org, admin, "Port scan", "PORT_SCAN",
                "{\"threshold\":20,\"windowSeconds\":120,\"groupBy\":\"sourceIp\"}", Severity.MEDIUM, "T1046");
        seedRule(org, admin, "SQL injection", "SQL_INJECTION",
                "{\"threshold\":3,\"windowSeconds\":300,\"groupBy\":\"sourceIp\"}", Severity.HIGH, "T1190");
        seedRule(org, admin, "Malware on endpoint", "MALWARE",
                "{\"threshold\":1,\"windowSeconds\":3600,\"groupBy\":\"entityKey\"}", Severity.CRITICAL, "T1204.002");
        seedRule(org, admin, "Privilege escalation", "PRIVILEGE_ESCALATION",
                "{\"threshold\":1,\"windowSeconds\":3600,\"groupBy\":\"username\"}", Severity.HIGH, "T1068");
        seedRule(org, admin, "Data exfiltration", "DATA_EXFILTRATION",
                "{\"minBytes\":524288000,\"threshold\":1,\"windowSeconds\":3600,\"groupBy\":\"username\"}",
                Severity.CRITICAL, "T1048");
        seedRule(org, admin, "Phishing link click", "PHISHING",
                "{\"threshold\":1,\"windowSeconds\":3600,\"groupBy\":\"username\"}", Severity.HIGH, "T1566.002");
        seedRule(org, admin, "Traffic flood (DDoS)", "DDOS",
                "{\"threshold\":150,\"windowSeconds\":60,\"groupBy\":\"entityKey\"}", Severity.HIGH, "T1498");

        int added = (int) detectionRuleRepository.count() - before;
        if (added > 0) {
            log.info("Seeded {} detection rule(s).", added);
        }
    }

    private void seedRule(Organization org, User admin, String name, String ruleType,
                          String config, Severity severity, String mitre) {
        if (detectionRuleRepository.existsByName(name)) {
            return;
        }
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
