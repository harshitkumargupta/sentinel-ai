-- SentinelAI :: optional client-supplied event id for idempotent ingestion.
-- NULL allowed (and repeated) for events without a client id; unique per org when present.
ALTER TABLE security_events
    ADD COLUMN client_event_id VARCHAR(100) NULL AFTER org_id;

CREATE UNIQUE INDEX uk_events_org_client ON security_events (org_id, client_event_id);
