-- SentinelAI :: Flyway baseline (Phase 0/1)
-- The real domain model (events, incidents, rules, users, audit) arrives in Phase 2.
-- This baseline only proves migrations run and the app connects to MySQL.

CREATE TABLE IF NOT EXISTS app_info (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    `key`       VARCHAR(64)  NOT NULL,
    `value`     VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_info_key (`key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO app_info (`key`, `value`)
VALUES ('schema_phase', 'phase-0-1-scaffold')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);
