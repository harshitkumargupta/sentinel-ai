package com.sentinelai.auth.domain;

/**
 * Platform roles, in increasing privilege. Order must match the {@code ENUM(...)}
 * in migration {@code V2__users.sql}.
 */
public enum Role {
    ADMIN,
    ANALYST,
    VIEWER
}
