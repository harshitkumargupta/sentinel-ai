-- SentinelAI :: normalized event outcome (QRadar-style SUCCESS / FAILURE) for parsing and search.
ALTER TABLE security_events
    ADD COLUMN outcome ENUM('SUCCESS','FAILURE','UNKNOWN') NOT NULL DEFAULT 'UNKNOWN' AFTER severity,
    ADD KEY idx_events_outcome (outcome);

UPDATE security_events SET outcome = 'FAILURE' WHERE event_type = 'FAILED_LOGIN';
UPDATE security_events SET outcome = 'SUCCESS' WHERE event_type = 'LOGIN_SUCCESS';
