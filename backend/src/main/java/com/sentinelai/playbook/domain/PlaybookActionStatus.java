package com.sentinelai.playbook.domain;

/**
 * Lifecycle of an automated/assisted response action. Order must match the
 * {@code ENUM(...)} in migration {@code V14__playbook_actions.sql}.
 */
public enum PlaybookActionStatus {
    PROPOSED,
    APPROVED,
    EXECUTED,
    ROLLED_BACK
}
