package com.sentinelai.common.bootstrap;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.service.PasswordPolicy;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * One-time production bootstrap: if the database has no users yet and {@code ADMIN_USERNAME} /
 * {@code ADMIN_PASSWORD} are provided via the environment, create a single ADMIN account so the
 * instance is usable (and deploy smoke tests can log in). Idempotent — it never runs once any user
 * exists, and it never logs the password. The password must satisfy the configured policy.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProdAdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return; // never touch an initialized system
        }
        String username = System.getenv("ADMIN_USERNAME");
        String password = System.getenv("ADMIN_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.info("No users and no ADMIN_USERNAME/ADMIN_PASSWORD provided — skipping admin bootstrap.");
            return;
        }
        Organization org = organizationRepository.findById(1L).orElse(null);
        if (org == null) {
            log.warn("Default Org (id 1) not found — skipping admin bootstrap.");
            return;
        }
        passwordPolicy.validate(password); // fail fast on a weak bootstrap password
        userRepository.save(User.builder()
                .org(org)
                .username(username)
                .email(username + "@sentinel.local")
                .passwordHash(passwordEncoder.encode(password))
                .role(Role.ADMIN)
                .enabled(true)
                .build());
        log.info("Bootstrapped initial ADMIN user '{}' (prod).", username);
    }
}
