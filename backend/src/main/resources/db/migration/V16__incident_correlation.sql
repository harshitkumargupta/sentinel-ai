-- SentinelAI :: incident correlation key -- lets the detection engine dedupe/correlate
-- repeated firings of a rule for the same entity into one incident.
ALTER TABLE incidents
    ADD COLUMN correlation_key VARCHAR(255) NULL AFTER feedback;

CREATE INDEX idx_incidents_correlation_key ON incidents (correlation_key);
