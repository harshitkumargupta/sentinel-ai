# SentinelAI — API Contracts (initial draft)

All endpoints are under `/api`. Payloads are JSON. These are **initial contracts** for
Phase 0/1 planning — only `GET /api/health` is implemented so far; the rest are the
target shape that later phases fill in. Interactive docs: `/swagger-ui.html`.

Auth: from the auth phase, protected endpoints require `Authorization: Bearer <jwt>`.
Roles: `VIEWER` < `ANALYST` < `ADMIN` (see event-taxonomy.md).

---

## Health

### `GET /api/health`  *(implemented)*
Liveness check. Public.
```json
{ "status": "UP", "service": "sentinel-ai", "timestamp": "2026-10-03T12:00:00Z" }
```

---

## Auth  *(planned)*

### `POST /api/auth/login`
```json
// request
{ "email": "analyst@sentinel.ai", "password": "••••••" }
// 200
{ "token": "<jwt>", "expiresIn": 3600, "user": { "id": 1, "email": "...", "role": "ANALYST" } }
```

### `POST /api/auth/refresh`
```json
{ "token": "<new-jwt>", "expiresIn": 3600 }
```

### `GET /api/auth/me`  → current user
```json
{ "id": 1, "email": "analyst@sentinel.ai", "role": "ANALYST" }
```

---

## Events  *(planned)*

### `GET /api/events`  — list/filter  *(VIEWER+)*
Query params: `type`, `severity`, `from`, `to`, `page`, `size`.
```json
{
  "content": [
    { "id": 100, "type": "FAILED_LOGIN", "severity": "LOW",
      "sourceIp": "203.0.113.5", "subject": "jdoe", "occurredAt": "2026-10-03T11:59:00Z" }
  ],
  "page": 0, "size": 20, "totalElements": 1
}
```

### `POST /api/events`  — ingest an event  *(service/ANALYST+)*
```json
{ "type": "FAILED_LOGIN", "sourceIp": "203.0.113.5", "subject": "jdoe",
  "occurredAt": "2026-10-03T11:59:00Z", "metadata": { "reason": "BAD_PASSWORD" } }
```

### `GET /api/events/{id}`  *(VIEWER+)*

---

## Incidents  *(planned)*

### `GET /api/incidents`  — list/filter  *(VIEWER+)*
Query params: `status` (OPEN/INVESTIGATING/RESOLVED/FALSE_POSITIVE), `severity`, `assignee`.

### `GET /api/incidents/{id}`  *(VIEWER+)* — incident with linked events.

### `PATCH /api/incidents/{id}`  — triage  *(ANALYST+)*
```json
{ "status": "INVESTIGATING", "assigneeId": 1 }
```

### `POST /api/incidents/{id}/comments`  *(ANALYST+)*
```json
{ "body": "Confirmed brute force from a single ASN; blocking at the edge." }
```

---

## Rules (detection)  *(planned)*

### `GET /api/rules`  *(ANALYST+)* — list detection rules.

### `POST /api/rules`  *(ADMIN)*
```json
{ "name": "Brute force - 10 fails / 5 min",
  "eventType": "FAILED_LOGIN", "window": "PT5M", "threshold": 10,
  "severity": "HIGH", "enabled": true }
```

### `PUT /api/rules/{id}`  *(ADMIN)*  ·  `DELETE /api/rules/{id}`  *(ADMIN)*

---

## Dashboard  *(planned)*

### `GET /api/dashboard/summary`  *(VIEWER+)*
```json
{
  "openIncidents": 3,
  "eventsLast24h": 1420,
  "activeRules": 7,
  "topRisks": [
    { "entity": "user:jdoe", "score": 82 },
    { "entity": "ip:203.0.113.5", "score": 76 }
  ]
}
```

### `GET /api/dashboard/trends?window=7d`  *(VIEWER+)* — time-series counts by event type.
