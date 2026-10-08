-- SentinelAI :: vulnerability findings (scanner CSV import) attached to inventoried assets.
CREATE TABLE vulnerabilities (
    id          BIGINT                                   NOT NULL AUTO_INCREMENT,
    org_id      BIGINT                                   NOT NULL,
    asset_id    BIGINT                                   NOT NULL,
    cve_id      VARCHAR(30)                              NOT NULL,
    severity    ENUM('LOW','MEDIUM','HIGH','CRITICAL')   NOT NULL,
    description VARCHAR(500)                             NULL,
    status      ENUM('OPEN','FIXED')                     NOT NULL DEFAULT 'OPEN',
    created_at  DATETIME(6)                              NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)                              NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_vuln_asset_cve (asset_id, cve_id),
    KEY idx_vuln_org_status (org_id, status),
    CONSTRAINT fk_vuln_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vuln_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
