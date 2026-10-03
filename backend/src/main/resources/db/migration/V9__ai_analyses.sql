-- SentinelAI :: ai_analyses (ai module)
CREATE TABLE ai_analyses (
    id                BIGINT                                                                             NOT NULL AUTO_INCREMENT,
    incident_id       BIGINT                                                                             NOT NULL,
    agent_type        ENUM('THREAT_ANALYSIS', 'CORRELATION', 'ROOT_CAUSE', 'RESPONSE_RECOMMENDATION')    NOT NULL,
    prompt            TEXT                                                                               NULL,
    output            TEXT                                                                               NULL,
    confidence        DECIMAL(5, 2)                                                                      NULL,
    evidence_event_ids JSON                                                                              NULL,
    validation_status ENUM('VALID', 'REJECTED', 'FALLBACK')                                              NULL,
    model_name        VARCHAR(100)                                                                       NULL,
    latency_ms        INT                                                                                NULL,
    status            ENUM('PENDING', 'APPROVED', 'REJECTED', 'MODIFIED')                                NOT NULL DEFAULT 'PENDING',
    reviewed_by       BIGINT                                                                             NULL,
    created_at        DATETIME(6)                                                                        NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ai_analyses_incident (incident_id),
    KEY idx_ai_analyses_status (status),
    KEY idx_ai_analyses_reviewed_by (reviewed_by),
    CONSTRAINT fk_ai_analyses_incident FOREIGN KEY (incident_id) REFERENCES incidents (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_analyses_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
