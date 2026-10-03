-- SentinelAI :: correlation layer -- alerts grouped into incidents + an incident timeline.

-- Join of alerts to the incident they were correlated into (idempotent via composite PK).
CREATE TABLE incident_alerts (
    incident_id BIGINT      NOT NULL,
    alert_id    BIGINT      NOT NULL,
    added_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (incident_id, alert_id),
    KEY idx_incident_alerts_alert (alert_id),
    CONSTRAINT fk_incident_alerts_incident FOREIGN KEY (incident_id) REFERENCES incidents (id) ON DELETE CASCADE,
    CONSTRAINT fk_incident_alerts_alert FOREIGN KEY (alert_id) REFERENCES alerts (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Append-only audit of everything that happened to an incident.
CREATE TABLE incident_timeline (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    incident_id BIGINT      NOT NULL,
    type        VARCHAR(50) NOT NULL,
    actor       VARCHAR(100) NULL,
    detail      JSON        NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_incident_timeline_incident (incident_id),
    CONSTRAINT fk_incident_timeline_incident FOREIGN KEY (incident_id) REFERENCES incidents (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
