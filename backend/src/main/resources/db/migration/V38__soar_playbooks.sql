-- SentinelAI :: SOAR playbooks — trigger + ordered steps — and their run history.
CREATE TABLE soar_playbooks (
    id                   BIGINT                                  NOT NULL AUTO_INCREMENT,
    org_id               BIGINT                                  NOT NULL,
    name                 VARCHAR(100)                            NOT NULL,
    description          VARCHAR(500)                            NULL,
    trigger_rule_type    VARCHAR(50)                             NULL,
    trigger_min_severity ENUM('LOW','MEDIUM','HIGH','CRITICAL')  NULL,
    steps                JSON                                    NOT NULL,
    enabled              TINYINT(1)                              NOT NULL DEFAULT 1,
    auto_run             TINYINT(1)                              NOT NULL DEFAULT 0,
    created_at           DATETIME(6)                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_soar_playbooks_org_name (org_id, name),
    CONSTRAINT fk_soar_playbooks_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE soar_runs (
    id           BIGINT                                   NOT NULL AUTO_INCREMENT,
    org_id       BIGINT                                   NOT NULL,
    playbook_id  BIGINT                                   NULL,
    incident_id  BIGINT                                   NULL,
    status       ENUM('SUCCEEDED','PARTIAL','FAILED')     NOT NULL,
    step_results JSON                                     NOT NULL,
    triggered_by VARCHAR(100)                             NOT NULL,
    created_at   DATETIME(6)                              NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_soar_runs_org (org_id, created_at),
    CONSTRAINT fk_soar_runs_playbook FOREIGN KEY (playbook_id) REFERENCES soar_playbooks (id) ON DELETE SET NULL,
    CONSTRAINT fk_soar_runs_incident FOREIGN KEY (incident_id) REFERENCES incidents (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO soar_playbooks (org_id, name, description, trigger_rule_type, trigger_min_severity, steps) VALUES
 (1, 'Brute Force Response', 'Open the case, alert the SOC, propose blocking the source IP and watchlist it.',
  'BRUTE_FORCE', 'HIGH',
  '[{"type":"CREATE_CASE","priority":"P2"},{"type":"NOTIFY"},{"type":"PROPOSE_BLOCK_IP"},{"type":"ADD_TO_WATCHLIST","set":"Watchlist IPs"}]'),
 (1, 'Data Exfiltration Response', 'Escalate to P1, alert the SOC, propose disabling the account and watchlist the user.',
  'DATA_EXFILTRATION', 'HIGH',
  '[{"type":"CREATE_CASE","priority":"P1"},{"type":"NOTIFY"},{"type":"PROPOSE_DISABLE_USER"},{"type":"ADD_TO_WATCHLIST","set":"Watchlist users"}]');
