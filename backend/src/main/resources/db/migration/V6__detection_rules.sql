-- SentinelAI :: detection_rules (detection module)
CREATE TABLE detection_rules (
    id          BIGINT                                     NOT NULL AUTO_INCREMENT,
    name        VARCHAR(150)                               NOT NULL,
    description TEXT                                       NULL,
    rule_type   VARCHAR(50)                                NOT NULL,
    config      JSON                                       NULL,
    enabled     TINYINT(1)                                 NOT NULL DEFAULT 1,
    severity    ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')  NOT NULL,
    created_by  BIGINT                                     NULL,
    created_at  DATETIME(6)                                NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)                                NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_detection_rules_name (name),
    KEY idx_detection_rules_enabled (enabled),
    KEY idx_detection_rules_created_by (created_by),
    CONSTRAINT fk_detection_rules_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
