package com.sentinelai.auth.dto;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        Long orgId,
        String username,
        String email,
        Role role,
        boolean enabled,
        Instant lastLoginAt,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getOrg().getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }
}
