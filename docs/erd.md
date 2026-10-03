# SentinelAI — Entity-Relationship Diagram (Phase 2)

The domain model created in Phase 2 (Flyway migrations `V2`–`V9`). Enum columns use native
MySQL `ENUM`; `raw_payload`, `config`, and `details` are `JSON`.

```mermaid
erDiagram
    users ||--o{ incidents : "assigned_to / created_by"
    users ||--o{ detection_rules : "created_by"
    users ||--o{ ai_analyses : "reviewed_by"
    users ||--o{ audit_logs : "actor_id"
    users ||--o{ notifications : "user_id"

    incidents ||--o{ incident_events : "incident_id"
    security_events ||--o{ incident_events : "event_id"
    incidents ||--o{ ai_analyses : "incident_id"
    incidents ||--o{ notifications : "incident_id (nullable)"

    users {
        bigint id PK
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
        enum event_type "FAILED_LOGIN|BRUTE_FORCE|SUSPICIOUS_LOGIN|API_ABUSE|ABNORMAL_ACCESS|OTHER"
        enum severity "LOW|MEDIUM|HIGH|CRITICAL"
        varchar source_ip
        varchar username
        varchar user_agent
        varchar resource
        varchar asset_criticality
        json raw_payload
        datetime event_timestamp
        datetime ingested_at
    }

    incidents {
        bigint id PK
        varchar title
        text description
        enum status "OPEN|INVESTIGATING|CONTAINED|RESOLVED|FALSE_POSITIVE"
        enum severity "LOW|MEDIUM|HIGH|CRITICAL"
        int risk_score
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
        varchar name UK
        text description
        varchar rule_type
        json config
        tinyint enabled
        enum severity "LOW|MEDIUM|HIGH|CRITICAL"
        bigint created_by FK
        datetime created_at
        datetime updated_at
    }

    ai_analyses {
        bigint id PK
        bigint incident_id FK
        enum agent_type "THREAT_ANALYSIS|CORRELATION|ROOT_CAUSE|RESPONSE_RECOMMENDATION"
        text prompt
        text output
        decimal confidence
        enum status "PENDING|APPROVED|REJECTED|MODIFIED"
        bigint reviewed_by FK
        datetime created_at
    }

    audit_logs {
        bigint id PK
        bigint actor_id FK
        varchar action
        varchar entity_type
        bigint entity_id
        json details
        varchar ip_address
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
```

## Relationships & delete behavior

| Child → Parent | Column | ON DELETE | Rationale |
|---|---|---|---|
| incidents → users | `assigned_to` | SET NULL | Keep the incident if the assignee is removed. |
| incidents → users | `created_by` | SET NULL | Keep the incident if the author is removed. |
| incident_events → incidents | `incident_id` | CASCADE | Links are meaningless without the incident. |
| incident_events → security_events | `event_id` | CASCADE | Links are meaningless without the event. |
| detection_rules → users | `created_by` | SET NULL | Keep the rule if the author is removed. |
| ai_analyses → incidents | `incident_id` | CASCADE | Analyses belong to their incident. |
| ai_analyses → users | `reviewed_by` | SET NULL | Keep the analysis if the reviewer is removed. |
| audit_logs → users | `actor_id` | SET NULL | Audit trail must survive user deletion (insert-only). |
| notifications → users | `user_id` | CASCADE | Notifications belong to their user. |
| notifications → incidents | `incident_id` | SET NULL | Notification can outlive the incident it referenced. |

`incident_events` is the many-to-many join between `incidents` and `security_events`, modeled
in JPA as an explicit join entity (composite key `incident_id + event_id`) so the link can
carry its own `added_at` timestamp.
