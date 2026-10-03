package com.sentinelai.common.bootstrap;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds dev-only data: one user per role and a few sample detection rules.
 *
 * <p>Active only under the {@code dev} profile and idempotent (it no-ops if data already
 * exists), so it is safe to run on every startup and never touches test or prod databases.
 * Dev passwords are documented in the project README.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final DetectionRuleRepository detectionRuleRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(String... args) {
        seedUsers();
        seedDetectionRules();
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            log.debug("Users already present — skipping user seed.");
            return;
        }
        userRepository.save(buildUser("admin", "admin@sentinel.ai", "Admin@123", Role.ADMIN));
        userRepository.save(buildUser("analyst", "analyst@sentinel.ai", "Analyst@123", Role.ANALYST));
        userRepository.save(buildUser("viewer", "viewer@sentinel.ai", "Viewer@123", Role.VIEWER));
        log.info("Seeded 3 dev users (admin/analyst/viewer).");
    }

    private User buildUser(String username, String email, String rawPassword, Role role) {
        return User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .enabled(true)
                .build();
    }

    private void seedDetectionRules() {
        if (detectionRuleRepository.count() > 0) {
            log.debug("Detection rules already present — skipping rule seed.");
            return;
        }
        User admin = userRepository.findByUsername("admin").orElse(null);

        detectionRuleRepository.save(DetectionRule.builder()
                .name("Brute force: 10 failed logins in 5 minutes")
                .description("Raise a HIGH incident when a single account or IP accumulates "
                        + "10+ FAILED_LOGIN events within 5 minutes.")
                .ruleType("THRESHOLD")
                .config("{\"eventType\":\"FAILED_LOGIN\",\"threshold\":10,\"windowSeconds\":300,\"groupBy\":\"username\"}")
                .enabled(true)
                .severity(Severity.HIGH)
                .createdBy(admin)
                .build());

        detectionRuleRepository.save(DetectionRule.builder()
                .name("Impossible travel")
                .description("Two successful logins for the same user from locations too far apart "
                        + "to travel between in the elapsed time.")
                .ruleType("GEO_VELOCITY")
                .config("{\"eventType\":\"SUSPICIOUS_LOGIN\",\"maxKmPerHour\":900}")
                .enabled(true)
                .severity(Severity.HIGH)
                .createdBy(admin)
                .build());

        detectionRuleRepository.save(DetectionRule.builder()
                .name("API abuse: request flood")
                .description("A single client exceeding 1000 requests per minute, or a high 4xx/5xx ratio.")
                .ruleType("RATE_LIMIT")
                .config("{\"eventType\":\"API_ABUSE\",\"requestsPerMinute\":1000,\"errorRatio\":0.5}")
                .enabled(true)
                .severity(Severity.MEDIUM)
                .createdBy(admin)
                .build());

        log.info("Seeded 3 sample detection rules.");
    }
}
