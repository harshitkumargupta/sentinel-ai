package com.sentinelai.auth.service;

import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.dto.CreateUserRequest;
import com.sentinelai.auth.dto.UpdateUserRequest;
import com.sentinelai.auth.dto.UserResponse;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.ConflictException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest req, AppUserPrincipal actor) {
        if (userRepository.existsByUsername(req.username())) {
            throw new ConflictException("Username already taken");
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email already registered");
        }
        User user = userRepository.save(User.builder()
                .org(organizationRepository.getReferenceById(actor.getOrgId()))
                .username(req.username())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(req.role())
                .enabled(true)
                .build());

        auditService.record(actor.getOrgId(), actor.getUserId(), "USER_CREATE", "user", user.getId(),
                "{\"username\":\"" + user.getUsername() + "\",\"role\":\"" + user.getRole() + "\"}", null);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req, AppUserPrincipal actor) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
        if (req.email() != null) {
            user.setEmail(req.email());
        }
        if (req.role() != null) {
            user.setRole(req.role());
        }
        if (req.enabled() != null) {
            user.setEnabled(req.enabled());
        }
        userRepository.save(user);
        auditService.record(actor.getOrgId(), actor.getUserId(), "USER_UPDATE", "user", user.getId(),
                "{\"email\":\"" + user.getEmail() + "\",\"role\":\"" + user.getRole()
                        + "\",\"enabled\":" + user.isEnabled() + "}", null);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse disable(Long id, AppUserPrincipal actor) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
        user.setEnabled(false);
        userRepository.save(user);
        auditService.record(actor.getOrgId(), actor.getUserId(), "USER_DISABLE", "user", user.getId(),
                null, null);
        return UserResponse.from(user);
    }
}
