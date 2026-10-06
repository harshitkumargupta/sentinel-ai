-- SentinelAI :: audit_logs (audit module) -- insert-only, tamper-evident hash chain
CREATE TABLE audit_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    org_id      BIGINT       NOT NULL,
    actor_id    BIGINT       NULL,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NULL,
    entity_id   BIGINT       NULL,
    details     JSON         NULL,
    ip_address  VARCHAR(45)  NULL,
    prev_hash   CHAR(64)     NULL,
    entry_hash  CHAR(64)     NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_audit_logs_org (org_id),
    KEY idx_audit_logs_actor (actor_id),
    KEY idx_audit_logs_action (action),
    KEY idx_audit_logs_entity (entity_type, entity_id),
    CONSTRAINT fk_audit_logs_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
