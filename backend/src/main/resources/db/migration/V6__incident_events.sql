-- SentinelAI :: incident_events (many-to-many join incidents <-> security_events)
CREATE TABLE incident_events (
    incident_id BIGINT      NOT NULL,
    event_id    BIGINT      NOT NULL,
    added_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (incident_id, event_id),
    KEY idx_incident_events_event (event_id),
    CONSTRAINT fk_incident_events_incident FOREIGN KEY (incident_id) REFERENCES incidents (id) ON DELETE CASCADE,
    CONSTRAINT fk_incident_events_event FOREIGN KEY (event_id) REFERENCES security_events (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
