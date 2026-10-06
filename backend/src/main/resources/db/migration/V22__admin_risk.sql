-- SentinelAI :: admin-action risk — per-admin baselines + risk-gated pending actions.
CREATE TABLE admin_baselines (
    user_id    BIGINT       NOT NULL,
    metric     VARCHAR(100) NOT NULL,
    data       JSON         NULL,
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, metric),
    CONSTRAINT fk_admin_baselines_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE pending_admin_actions (
    id             BIGINT                                                        NOT NULL AUTO_INCREMENT,
    org_id         BIGINT                                                        NOT NULL,
    requested_by   BIGINT                                                        NOT NULL,
    action         VARCHAR(100)                                                  NOT NULL,
    entity_type    VARCHAR(100)                                                  NULL,
    entity_id      BIGINT                                                        NULL,
    payload        JSON                                                          NULL,
    risk_score     INT                                                           NOT NULL,
    risk_breakdown JSON                                                          NULL,
    status         ENUM('PENDING','APPROVED','REJECTED','EXPIRED','EXECUTED')    NOT NULL DEFAULT 'PENDING',
    approved_by    BIGINT                                                        NULL,
    expires_at     DATETIME(6)                                                   NOT NULL,
    created_at     DATETIME(6)                                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at    DATETIME(6)                                                   NULL,
    PRIMARY KEY (id),
    KEY idx_paa_status (status),
    KEY idx_paa_requested_by (requested_by),
    CONSTRAINT fk_paa_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_paa_requested_by FOREIGN KEY (requested_by) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_paa_approved_by FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
