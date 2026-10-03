-- SentinelAI :: auth hardening -- account lockout fields + hashed refresh tokens
ALTER TABLE users
    ADD COLUMN failed_login_attempts INT         NOT NULL DEFAULT 0 AFTER enabled,
    ADD COLUMN locked_until          DATETIME(6) NULL             AFTER failed_login_attempts;

CREATE TABLE refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked    TINYINT(1)  NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_user (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
