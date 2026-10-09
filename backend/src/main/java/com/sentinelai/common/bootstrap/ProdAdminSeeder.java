package com.sentinelai.common.bootstrap;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.service.PasswordPolicy;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.site.domain.UserSiteAccess;
import com.sentinelai.site.repository.UserSiteAccessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Production bootstrap: seeds the initial ADMIN account (and optional demo accounts)
 * from environment variables so the instance is usable after first deploy.
 * Per-username idempotent — skips any username that already exists.
 *
 * <p>Required: ADMIN_USERNAME, ADMIN_PASSWORD<br>
 * Optional demo accounts (set all three to enable): ANALYST_PASSWORD, VIEWER_PASSWORD
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
    private final UserSiteAccessRepository userSiteAccessRepository;

    @Override
    public void run(String... args) {
        String adminUsername = System.getenv("ADMIN_USERNAME");
        String adminPassword = System.getenv("ADMIN_PASSWORD");
        if (adminUsername == null || adminUsername.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            log.info("ADMIN_USERNAME/ADMIN_PASSWORD not set — skipping prod user bootstrap.");
            return;
        }

        Organization org = organizationRepository.findById(1L).orElse(null);
        if (org == null) {
            log.warn("Default org (id=1) not found — skipping prod user bootstrap.");
            return;
        }

        seed(org, adminUsername, adminPassword, Role.ADMIN);

        String analystPassword = System.getenv("ANALYST_PASSWORD");
        String viewerPassword  = System.getenv("VIEWER_PASSWORD");
        if (analystPassword != null && !analystPassword.isBlank()
                && viewerPassword != null && !viewerPassword.isBlank()) {
            seed(org, "analyst", analystPassword,  Role.ANALYST);
            seed(org, "viewer",  viewerPassword,   Role.VIEWER);
        }
    }

    private void seed(Organization org, String username, String rawPassword, Role role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        passwordPolicy.validate(rawPassword);
        User user = userRepository.save(User.builder()
                .org(org)
                .username(username)
                .email(username + "@sentinel.local")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .enabled(true)
                .build());
        if (!userSiteAccessRepository.existsById_UserIdAndId_SiteId(user.getId(), 1L)) {
            userSiteAccessRepository.save(new UserSiteAccess(user.getId(), 1L));
        }
        log.info("Bootstrapped {} user '{}' (prod).", role, username);
    }
}
