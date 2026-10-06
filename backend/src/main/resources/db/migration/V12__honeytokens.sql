-- SentinelAI :: honeytokens (honeytoken module) -- decoy credentials/resources
CREATE TABLE honeytokens (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    org_id          BIGINT       NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    value_hash      CHAR(64)     NOT NULL,
    description     VARCHAR(255) NULL,
    triggered_count INT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_honeytokens_org (org_id),
    CONSTRAINT fk_honeytokens_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
