-- SentinelAI :: multi-site tenancy under an org, with per-site ingest API keys.
CREATE TABLE sites (
    id            BIGINT                          NOT NULL AUTO_INCREMENT,
    org_id        BIGINT                          NOT NULL,
    name          VARCHAR(150)                    NOT NULL,
    domain        VARCHAR(255)                    NULL,
    status        ENUM('ACTIVE', 'DISABLED')      NOT NULL DEFAULT 'ACTIVE',
    last_event_at DATETIME(6)                     NULL,
    created_at    DATETIME(6)                     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_sites_org (org_id),
    CONSTRAINT fk_sites_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO sites (id, org_id, name, domain, status) VALUES (1, 1, 'Default Site', 'default.local', 'ACTIVE');

CREATE TABLE api_keys (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    site_id      BIGINT       NOT NULL,
    key_hash     CHAR(64)     NOT NULL,
    scope        VARCHAR(30)  NOT NULL DEFAULT 'INGEST',
    last_used_at DATETIME(6)  NULL,
    revoked_at   DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_keys_hash (key_hash),
    KEY idx_api_keys_site (site_id),
    CONSTRAINT fk_api_keys_site FOREIGN KEY (site_id) REFERENCES sites (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_site_access (
    user_id    BIGINT      NOT NULL,
    site_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, site_id),
    KEY idx_user_site_access_site (site_id),
    CONSTRAINT fk_usa_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_usa_site FOREIGN KEY (site_id) REFERENCES sites (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Every existing user can see the default site.
INSERT INTO user_site_access (user_id, site_id) SELECT id, 1 FROM users;

-- Tag existing data with the default site.
ALTER TABLE security_events ADD COLUMN site_id BIGINT NULL AFTER org_id,
    ADD KEY idx_events_site (site_id),
    ADD CONSTRAINT fk_events_site FOREIGN KEY (site_id) REFERENCES sites (id) ON DELETE SET NULL;
UPDATE security_events SET site_id = 1;

ALTER TABLE incidents ADD COLUMN site_id BIGINT NULL AFTER org_id,
    ADD KEY idx_incidents_site (site_id),
    ADD CONSTRAINT fk_incidents_site FOREIGN KEY (site_id) REFERENCES sites (id) ON DELETE SET NULL;
UPDATE incidents SET site_id = 1;

ALTER TABLE detection_rules ADD COLUMN site_id BIGINT NULL AFTER org_id,
    ADD KEY idx_rules_site (site_id),
    ADD CONSTRAINT fk_rules_site FOREIGN KEY (site_id) REFERENCES sites (id) ON DELETE SET NULL;
UPDATE detection_rules SET site_id = 1;
