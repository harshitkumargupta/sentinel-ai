-- SentinelAI :: Demo Center — event types for the extra attack scenarios (port scan, web attacks,
-- endpoint malware, privilege escalation, data transfer, phishing, network flood) and a benign
-- successful-login type for baselines. Appended to the end so existing ordinals are unchanged.
ALTER TABLE security_events
    MODIFY event_type ENUM('FAILED_LOGIN','BRUTE_FORCE','SUSPICIOUS_LOGIN','API_ABUSE','ABNORMAL_ACCESS',
                           'HONEYTOKEN_ACCESS','OTHER','PROMPT_INJECTION',
                           'LOGIN_SUCCESS','PORT_SCAN','SQL_INJECTION','MALWARE_DETECTED',
                           'PRIVILEGE_ESCALATION','DATA_TRANSFER','PHISHING_CLICK','NETWORK_FLOOD') NOT NULL;
