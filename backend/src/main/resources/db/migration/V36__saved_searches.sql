-- SentinelAI :: saved event searches (per user), optionally pinned to the dashboard as widgets.
CREATE TABLE saved_searches (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    org_id     BIGINT        NOT NULL,
    owner_id   BIGINT        NOT NULL,
    name       VARCHAR(100)  NOT NULL,
    query      VARCHAR(1000) NULL,
    filters    JSON          NULL,
    pinned     TINYINT(1)    NOT NULL DEFAULT 0,
    created_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_saved_searches_owner_name (owner_id, name),
    CONSTRAINT fk_saved_searches_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_saved_searches_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
