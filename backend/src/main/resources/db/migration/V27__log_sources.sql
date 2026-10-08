-- SentinelAI :: Log Sources (QRadar-style collection) on top of sites.
-- A site with a source type is a log source: it owns ingest API keys (hashed, V21), and now records
-- what kind of data it sends and how many records failed to parse.
ALTER TABLE sites
    ADD COLUMN source_type       ENUM('GENERIC','WEB_SERVER','AUTH','FIREWALL','APPLICATION') NOT NULL DEFAULT 'GENERIC' AFTER domain,
    ADD COLUMN description       VARCHAR(500)  NULL AFTER source_type,
    ADD COLUMN parse_error_count BIGINT        NOT NULL DEFAULT 0 AFTER last_event_at;

-- Per-source volume / events-per-second queries.
CREATE INDEX idx_events_site_ingested ON security_events (site_id, ingested_at);
