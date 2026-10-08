-- SentinelAI :: case management on incidents — a terminal CLOSED status, analyst priority, and
-- editable notes (who/when last edited).
ALTER TABLE incidents
    MODIFY status ENUM('OPEN','INVESTIGATING','CONTAINED','RESOLVED','FALSE_POSITIVE','CLOSED') NOT NULL DEFAULT 'OPEN',
    ADD COLUMN priority  ENUM('P1','P2','P3','P4') NOT NULL DEFAULT 'P3' AFTER severity,
    ADD COLUMN closed_at DATETIME(6) NULL AFTER resolved_at,
    ADD KEY idx_incidents_priority (priority);

UPDATE incidents SET priority = CASE severity
    WHEN 'CRITICAL' THEN 'P1' WHEN 'HIGH' THEN 'P2' WHEN 'MEDIUM' THEN 'P3' ELSE 'P4' END;

ALTER TABLE incident_notes
    ADD COLUMN updated_at DATETIME(6) NULL AFTER created_at,
    ADD COLUMN edited_by  BIGINT      NULL AFTER updated_at,
    ADD CONSTRAINT fk_incident_notes_editor FOREIGN KEY (edited_by) REFERENCES users (id) ON DELETE SET NULL;
