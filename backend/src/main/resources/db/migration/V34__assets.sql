-- SentinelAI :: asset inventory — hosts / IPs with owner, type, environment and criticality.
-- Events are linked to an asset at ingest (host entity key, source IP, or an IP in the resource);
-- incidents reach their assets through their events.
CREATE TABLE assets (
    id          BIGINT                                                                       NOT NULL AUTO_INCREMENT,
    org_id      BIGINT                                                                       NOT NULL,
    hostname    VARCHAR(255)                                                                 NULL,
    ip          VARCHAR(45)                                                                  NULL,
    owner       VARCHAR(150)                                                                 NULL,
    asset_type  ENUM('SERVER','WORKSTATION','LAPTOP','NETWORK','CLOUD','APPLICATION','OTHER') NOT NULL DEFAULT 'OTHER',
    environment ENUM('PRODUCTION','STAGING','DEVELOPMENT','CORPORATE')                       NOT NULL DEFAULT 'CORPORATE',
    criticality ENUM('LOW','MEDIUM','HIGH','CRITICAL')                                       NOT NULL DEFAULT 'MEDIUM',
    description VARCHAR(500)                                                                 NULL,
    created_at  DATETIME(6)                                                                  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)                                                                  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_assets_org_hostname (org_id, hostname),
    UNIQUE KEY uk_assets_org_ip (org_id, ip),
    CONSTRAINT fk_assets_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE security_events
    ADD COLUMN asset_id BIGINT NULL AFTER site_id,
    ADD KEY idx_events_asset (asset_id),
    ADD CONSTRAINT fk_events_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE SET NULL;
