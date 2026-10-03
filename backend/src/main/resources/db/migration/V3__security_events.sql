-- SentinelAI :: security_events (event module)
CREATE TABLE security_events (
    id                BIGINT                                                                              NOT NULL AUTO_INCREMENT,
    event_type        ENUM('FAILED_LOGIN', 'BRUTE_FORCE', 'SUSPICIOUS_LOGIN', 'API_ABUSE', 'ABNORMAL_ACCESS', 'OTHER') NOT NULL,
    severity          ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')                                           NOT NULL,
    source_ip         VARCHAR(45)                                                                         NULL,
    username          VARCHAR(100)                                                                        NULL,
    user_agent        VARCHAR(512)                                                                        NULL,
    resource          VARCHAR(255)                                                                        NULL,
    asset_criticality VARCHAR(20)                                                                         NULL,
    raw_payload       JSON                                                                                NULL,
    event_timestamp   DATETIME(6)                                                                         NOT NULL,
    ingested_at       DATETIME(6)                                                                         NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_events_timestamp (event_timestamp),
    KEY idx_events_source_ip (source_ip),
    KEY idx_events_severity (severity),
    KEY idx_events_type (event_type),
    KEY idx_events_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
