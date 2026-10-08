-- SentinelAI :: analyst notes on offenses (incidents).
CREATE TABLE incident_notes (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    incident_id BIGINT        NOT NULL,
    author_id   BIGINT        NULL,
    body        VARCHAR(2000) NOT NULL,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_incident_notes_incident (incident_id),
    CONSTRAINT fk_incident_notes_incident FOREIGN KEY (incident_id) REFERENCES incidents (id) ON DELETE CASCADE,
    CONSTRAINT fk_incident_notes_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
