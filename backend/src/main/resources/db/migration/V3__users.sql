-- SentinelAI :: users (auth module)
CREATE TABLE users (
    id            BIGINT                               NOT NULL AUTO_INCREMENT,
    org_id        BIGINT                               NOT NULL,
    username      VARCHAR(100)                         NOT NULL,
    email         VARCHAR(255)                         NOT NULL,
    password_hash VARCHAR(100)                         NOT NULL,
    role          ENUM('ADMIN', 'ANALYST', 'VIEWER')   NOT NULL,
    enabled       TINYINT(1)                           NOT NULL DEFAULT 1,
    created_at    DATETIME(6)                          NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)                          NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    last_login_at DATETIME(6)                          NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_org (org_id),
    CONSTRAINT fk_users_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
