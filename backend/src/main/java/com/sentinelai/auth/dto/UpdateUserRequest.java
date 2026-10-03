package com.sentinelai.auth.dto;

import com.sentinelai.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** All fields optional; only non-null fields are applied. */
public record UpdateUserRequest(
        @Email @Size(max = 255) String email,
        Role role,
        Boolean enabled) {
}
