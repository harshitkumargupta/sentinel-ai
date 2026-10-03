# SentinelAI — Entity-Relationship Diagram (Phase 2)

Domain model created in Phase 2 (Flyway migrations `V2`–`V14`, InnoDB / utf8mb4). Enum columns
use native MySQL `ENUM` (uppercase values); `*_payload` / `*_breakdown` / `config` / `details` /
`evidence_event_ids` / `dry_run_result` / `config_snapshot` are `JSON`; hashes are `CHAR(64)`.
Every org-scoped table references `organizations` via `org_id`.

```mermaid
erDiagram
    organizations ||--o{ users : org_id
    organizations ||--o{ security_events : org_id
    organizations ||--o{ incidents : org_id
    organizations ||--o{ detection_rules : org_id
    organizations ||--o{ audit_logs : org_id
    organizations ||--o{ honeytokens : org_id

    users ||--o{ incidents : "assigned_to / created_by"
    users ||--o{ detection_rules : created_by
    users ||--o{ ai_analyses : reviewed_by
    users ||--o{ audit_logs : actor_id
    users ||--o{ notifications : user_id
    users ||--o{ playbook_actions : approved_by

    incidents ||--o{ incident_events : incident_id
    security_events ||--o{ incident_events : event_id
    incidents ||--o{ ai_analyses : incident_id
    incidents ||--o{ notifications : "incident_id (nullable)"
    incidents ||--o{ playbook_actions : incident_id
    detection_rules ||--o{ backtest_runs : rule_id

    organizations {
        bigint id PK
        varchar name UK
        datetime created_at
    }
    users {
        bigint id PK
        bigint org_id FK
        varchar username UK
        varchar email UK
        varchar password_hash
        enum role "ADMIN|ANALYST|VIEWER"
        tinyint enabled
        datetime created_at
        datetime updated_at
        datetime last_login_at
    }
    security_events {
        bigint id PK
        bigint org_id FK
        enum event_type "FAILED_LOGIN|BRUTE_FORCE|SUSPICIOUS_LOGIN|API_ABUSE|ABNORMAL_ACCESS|HONEYTOKEN_ACCESS|OTHER"
        enum severity "LOW|MEDIUM|HIGH|CRITICAL"
        varchar source_ip
        varchar username
        varchar user_agent
        varchar resource
        tinyint asset_criticality
        json raw_payload
        varchar geo_country
        varchar geo_city
        tinyint is_honeytoken
        varchar entity_key
        varchar correlation_key
        datetime event_timestamp
        datetime ingested_at
    }
    incidents {
        bigint id PK
        bigint org_id FK
        varchar title
        text description
        enum status "OPEN|INVESTIGATING|CONTAINED|RESOLVED|FALSE_POSITIVE"
        enum severity "LOW|MEDIUM|HIGH|CRITICAL"
        int risk_score
        json risk_breakdown
        enum feedback "TRUE_POSITIVE|FALSE_POSITIVE|UNREVIEWED"
        bigint assigned_to FK
        bigint created_by FK
        datetime created_at
        datetime updated_at
        datetime resolved_at
    }
    incident_events {
        bigint incident_id PK,FK
        bigint event_id PK,FK
        datetime added_at
    }
    detection_rules {
        bigint id PK
        bigint org_id FK
        varchar name
        text description
        varchar rule_type
        json config
        tinyint enabled
        enum severity "LOW|MEDIUM|HIGH|CRITICAL"
        varchar mitre_technique
        int version
        bigint created_by FK
        datetime created_at
        datetime updated_at
    }
    backtest_runs {
        bigint id PK
        bigint rule_id FK
        datetime run_at
        int events_scanned
        int alerts_fired
        json config_snapshot
    }
    ai_analyses {
        bigint id PK
        bigint incident_id FK
        enum agent_type "THREAT_ANALYSIS|CORRELATION|ROOT_CAUSE|RESPONSE_RECOMMENDATION"
        text prompt
        text output
        decimal confidence
        json evidence_event_ids
        enum validation_status "VALID|REJECTED|FALLBACK"
        varchar model_name
        int latency_ms
        enum status "PENDING|APPROVED|REJECTED|MODIFIED"
        bigint reviewed_by FK
        datetime created_at
    }
    audit_logs {
        bigint id PK
        bigint org_id FK
        bigint actor_id FK
        varchar action
        varchar entity_type
        bigint entity_id
        json details
        varchar ip_address
        char prev_hash
        char entry_hash
        datetime created_at
    }
    notifications {
        bigint id PK
        bigint user_id FK
        bigint incident_id FK
        varchar message
        tinyint read_flag
        datetime created_at
    }
    honeytokens {
        bigint id PK
        bigint org_id FK
        varchar type
        char value_hash
        varchar description
        int triggered_count
        datetime created_at
    }
    entity_baselines {
        varchar entity_key PK
        varchar metric PK
        double mean_value
        double std_dev
        bigint sample_count
        datetime updated_at
    }
    playbook_actions {
        bigint id PK
        bigint incident_id FK
        varchar action_type
        enum status "PROPOSED|APPROVED|EXECUTED|ROLLED_BACK"
        json dry_run_result
        bigint approved_by FK
        datetime executed_at
        datetime created_at
    }
```

## Foreign keys & delete behavior

| Child → Parent | Column | ON DELETE |
|---|---|---|
| incident_events → incidents | incident_id | CASCADE |
| incident_events → security_events | event_id | CASCADE |
| backtest_runs → detection_rules | rule_id | CASCADE |
| incidents → users | assigned_to, created_by | SET NULL |
| detection_rules → users | created_by | SET NULL |
| ai_analyses → users | reviewed_by | SET NULL |
| audit_logs → users | actor_id | SET NULL |
| playbook_actions → users | approved_by | SET NULL |
| users / security_events / incidents / detection_rules / audit_logs / honeytokens → organizations | org_id | RESTRICT |
| ai_analyses → incidents | incident_id | RESTRICT |
| notifications → users | user_id | RESTRICT |
| notifications → incidents | incident_id | RESTRICT |
| playbook_actions → incidents | incident_id | RESTRICT |

CASCADE for owned links (`incident_events`, `backtest_runs`); SET NULL for optional user
references (so records survive user deletion); RESTRICT everywhere else.

`incident_events` and `entity_baselines` use composite primary keys. `audit_logs` is insert-only
(hash-chained via `prev_hash`/`entry_hash`); its repository exposes no update/delete.

## Indexes

Hot-path indexes: `event_timestamp`, `source_ip`, `severity`, `event_type`, `username`,
`entity_key`, `correlation_key`, composite `(org_id, event_timestamp)`, incident `status` and
`severity`, plus an index on every foreign-key column (InnoDB).
