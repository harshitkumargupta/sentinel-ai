package com.sentinelai.auth.repository;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CRUD and constraint tests for {@link UserRepository} against the real MySQL test database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private static User newUser(String username, String email, Role role) {
        return User.builder()
                .username(username)
                .email(email)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuv")
                .role(role)
                .enabled(true)
                .build();
    }

    @Test
    void savesAndReadsBack() {
        User saved = userRepository.save(newUser("alice", "alice@sentinel.ai", Role.ANALYST));

        assertThat(saved.getId()).isNotNull();
        // Auditing timestamps populated by the base auditable entity.
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<User> found = userRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("alice");
        assertThat(found.get().getRole()).isEqualTo(Role.ANALYST);
        assertThat(found.get().isEnabled()).isTrue();
    }

    @Test
    void findsByUsernameAndEmail() {
        userRepository.save(newUser("bob", "bob@sentinel.ai", Role.ADMIN));

        assertThat(userRepository.findByUsername("bob")).isPresent();
        assertThat(userRepository.findByEmail("bob@sentinel.ai")).isPresent();
        assertThat(userRepository.existsByUsername("bob")).isTrue();
        assertThat(userRepository.findByRole(Role.ADMIN)).extracting(User::getUsername).contains("bob");
    }

    @Test
    void updatesAndDeletes() {
        User saved = userRepository.save(newUser("carol", "carol@sentinel.ai", Role.VIEWER));
        Long id = saved.getId();

        saved.setLastLoginAt(Instant.now());
        saved.setRole(Role.ANALYST);
        userRepository.saveAndFlush(saved);

        User reloaded = userRepository.findById(id).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.ANALYST);
        assertThat(reloaded.getLastLoginAt()).isNotNull();

        userRepository.deleteById(id);
        assertThat(userRepository.findById(id)).isEmpty();
    }

    @Test
    void enforcesUniqueUsername() {
        userRepository.saveAndFlush(newUser("dave", "dave1@sentinel.ai", Role.VIEWER));

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(newUser("dave", "dave2@sentinel.ai", Role.VIEWER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
