-- SentinelAI :: entity_baselines (baseline module) -- rolling per-entity metric baselines
CREATE TABLE entity_baselines (
    entity_key   VARCHAR(255) NOT NULL,
    metric       VARCHAR(100) NOT NULL,
    mean_value   DOUBLE       NULL,
    std_dev      DOUBLE       NULL,
    sample_count BIGINT       NOT NULL DEFAULT 0,
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (entity_key, metric),
    KEY idx_entity_baselines_metric (metric)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
