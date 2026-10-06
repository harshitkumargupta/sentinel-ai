-- SentinelAI :: organizations (tenancy root) -- referenced by all org-scoped tables
CREATE TABLE organizations (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(150) NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_organizations_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO organizations (id, name) VALUES (1, 'Default Org');
