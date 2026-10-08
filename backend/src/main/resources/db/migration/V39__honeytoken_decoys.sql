-- SentinelAI :: honeytoken decoys — kind (username / API key / URL path), a display value (masked for
-- secrets) and when it was last touched. The real value is still stored only as SHA-256.
ALTER TABLE honeytokens
    ADD COLUMN kind              ENUM('USERNAME','API_KEY','URL_PATH','OTHER') NOT NULL DEFAULT 'OTHER' AFTER type,
    ADD COLUMN display_value     VARCHAR(120) NULL AFTER value_hash,
    ADD COLUMN last_triggered_at DATETIME(6)  NULL AFTER triggered_count,
    ADD KEY idx_honeytokens_hash (value_hash);
