-- SentinelAI :: detection coverage runs (simulator scenarios → detected/missed, MITRE matrix, gaps).
CREATE TABLE coverage_runs (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    org_id       BIGINT        NOT NULL,
    coverage_pct DECIMAL(5,1)  NOT NULL,
    detected     INT           NOT NULL,
    tested       INT           NOT NULL,
    report       JSON          NOT NULL,
    created_by   BIGINT        NULL,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_coverage_runs_org (org_id, created_at),
    CONSTRAINT fk_coverage_runs_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_coverage_runs_user FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
