-- SentinelAI :: simulator runs + ground-truth labels for the evaluation harness.
CREATE TABLE simulator_runs (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    run_id           VARCHAR(64)  NOT NULL,
    seed             BIGINT       NOT NULL,
    scenarios        VARCHAR(500) NOT NULL,
    intensity        INT          NOT NULL,
    events_generated INT          NOT NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_simulator_runs_run_id (run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE sim_labels (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    event_id      BIGINT      NOT NULL,
    scenario_id   VARCHAR(50) NOT NULL,
    run_id        VARCHAR(64) NOT NULL,
    is_attack     TINYINT(1)  NOT NULL,
    expected_rule VARCHAR(50) NULL,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_sim_labels_run (run_id),
    KEY idx_sim_labels_event (event_id),
    CONSTRAINT fk_sim_labels_event FOREIGN KEY (event_id) REFERENCES security_events (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
