-- SentinelAI :: Phase 12 — AI investigation pipeline.
-- Extend ai_analyses for the single evidence-validated investigation (3 stages in one row),
-- add recommendation details to playbook_actions, and add a PROMPT_INJECTION event type.

ALTER TABLE ai_analyses
    MODIFY agent_type ENUM('THREAT_ANALYSIS','CORRELATION','ROOT_CAUSE','RESPONSE_RECOMMENDATION','INVESTIGATION') NOT NULL,
    ADD COLUMN analysis_state    ENUM('QUEUED','RUNNING','COMPLETE','FAILED') NOT NULL DEFAULT 'QUEUED' AFTER validation_status,
    ADD COLUMN template_version  VARCHAR(20)     NULL,
    ADD COLUMN context_hash      CHAR(64)        NULL,
    ADD COLUMN faithfulness_score DECIMAL(5,2)   NULL,
    ADD COLUMN prompt_tokens     INT             NULL,
    ADD COLUMN completion_tokens INT             NULL,
    ADD COLUMN cost_usd          DECIMAL(10,6)   NULL,
    ADD COLUMN injection_detected TINYINT(1)     NOT NULL DEFAULT 0,
    ADD COLUMN review_note       VARCHAR(1000)   NULL,
    ADD KEY idx_ai_analyses_context (incident_id, context_hash);

ALTER TABLE playbook_actions
    ADD COLUMN target_ref  VARCHAR(255) NULL AFTER action_type,
    ADD COLUMN reason      VARCHAR(1000) NULL AFTER target_ref,
    ADD COLUMN analysis_id BIGINT       NULL AFTER reason,
    ADD CONSTRAINT fk_playbook_actions_analysis FOREIGN KEY (analysis_id) REFERENCES ai_analyses (id) ON DELETE SET NULL;

ALTER TABLE security_events
    MODIFY event_type ENUM('FAILED_LOGIN','BRUTE_FORCE','SUSPICIOUS_LOGIN','API_ABUSE','ABNORMAL_ACCESS','HONEYTOKEN_ACCESS','OTHER','PROMPT_INJECTION') NOT NULL;
