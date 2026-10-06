-- SentinelAI :: Phase 13 — SOAR-lite playbooks.
-- Extend playbook_actions for the full human-approved response lifecycle: proposer/approver
-- separation, risk level, dry-run + before/after state, expiry, and failure tracking.

ALTER TABLE playbook_actions
    MODIFY status ENUM('PROPOSED','APPROVED','EXECUTED','ROLLED_BACK','REJECTED','FAILED','EXPIRED') NOT NULL DEFAULT 'PROPOSED',
    ADD COLUMN proposed_by    BIGINT                                     NULL AFTER incident_id,
    ADD COLUMN risk_level     ENUM('LOW','MEDIUM','HIGH','CRITICAL')     NULL AFTER status,
    ADD COLUMN expires_at     DATETIME(6)                                NULL AFTER risk_level,
    ADD COLUMN before_state   JSON                                       NULL AFTER dry_run_result,
    ADD COLUMN after_state    JSON                                       NULL AFTER before_state,
    ADD COLUMN failure_reason VARCHAR(1000)                              NULL AFTER after_state,
    ADD COLUMN approved_at    DATETIME(6)                                NULL AFTER approved_by,
    ADD COLUMN rolled_back_at DATETIME(6)                                NULL AFTER executed_at,
    ADD CONSTRAINT fk_playbook_actions_proposed_by FOREIGN KEY (proposed_by) REFERENCES users (id) ON DELETE SET NULL;
