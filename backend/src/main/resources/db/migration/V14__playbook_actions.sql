-- SentinelAI :: playbook_actions (playbook module) -- proposed/approved response actions
CREATE TABLE playbook_actions (
    id             BIGINT                                                    NOT NULL AUTO_INCREMENT,
    incident_id    BIGINT                                                    NOT NULL,
    action_type    VARCHAR(100)                                              NOT NULL,
    status         ENUM('PROPOSED', 'APPROVED', 'EXECUTED', 'ROLLED_BACK')   NOT NULL DEFAULT 'PROPOSED',
    dry_run_result JSON                                                      NULL,
    approved_by    BIGINT                                                    NULL,
    executed_at    DATETIME(6)                                               NULL,
    created_at     DATETIME(6)                                               NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_playbook_actions_incident (incident_id),
    KEY idx_playbook_actions_status (status),
    KEY idx_playbook_actions_approved_by (approved_by),
    CONSTRAINT fk_playbook_actions_incident FOREIGN KEY (incident_id) REFERENCES incidents (id) ON DELETE RESTRICT,
    CONSTRAINT fk_playbook_actions_approved_by FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
