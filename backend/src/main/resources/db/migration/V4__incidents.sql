-- SentinelAI :: incidents (incident module)
CREATE TABLE incidents (
    id          BIGINT                                                                        NOT NULL AUTO_INCREMENT,
    title       VARCHAR(255)                                                                  NOT NULL,
    description TEXT                                                                          NULL,
    status      ENUM('OPEN', 'INVESTIGATING', 'CONTAINED', 'RESOLVED', 'FALSE_POSITIVE')      NOT NULL DEFAULT 'OPEN',
    severity    ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')                                     NOT NULL,
    risk_score  INT                                                                           NULL,
    assigned_to BIGINT                                                                        NULL,
    created_by  BIGINT                                                                        NULL,
    created_at  DATETIME(6)                                                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)                                                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    resolved_at DATETIME(6)                                                                   NULL,
    PRIMARY KEY (id),
    KEY idx_incidents_status (status),
    KEY idx_incidents_severity (severity),
    KEY idx_incidents_assigned_to (assigned_to),
    KEY idx_incidents_created_by (created_by),
    CONSTRAINT fk_incidents_assigned_to FOREIGN KEY (assigned_to) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_incidents_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
