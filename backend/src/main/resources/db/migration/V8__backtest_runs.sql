-- SentinelAI :: backtest_runs (detection module) -- results of replaying a rule over history
CREATE TABLE backtest_runs (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    rule_id         BIGINT      NOT NULL,
    run_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    events_scanned  INT         NULL,
    alerts_fired    INT         NULL,
    config_snapshot JSON        NULL,
    PRIMARY KEY (id),
    KEY idx_backtest_runs_rule (rule_id),
    CONSTRAINT fk_backtest_runs_rule FOREIGN KEY (rule_id) REFERENCES detection_rules (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
