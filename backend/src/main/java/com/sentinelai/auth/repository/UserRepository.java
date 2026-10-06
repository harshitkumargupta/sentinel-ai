package com.sentinelai.auth.repository;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByRole(Role role);

    // Org-scoped lookups (tenant isolation for admin user management).
    List<User> findByOrg_Id(Long orgId);

    Optional<User> findByIdAndOrg_Id(Long id, Long orgId);
}
