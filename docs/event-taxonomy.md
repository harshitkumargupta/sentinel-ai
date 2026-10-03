# SentinelAI — Event Taxonomy & Roles

## 1. Roles

SentinelAI uses three roles with increasing privilege:

| Role      | Can do |
|-----------|--------|
| `VIEWER`  | Read-only: view dashboard, events, and incidents. Cannot change anything. |
| `ANALYST` | Everything a VIEWER can, plus triage incidents (assign, change status, comment) and acknowledge events. |
| `ADMIN`   | Everything an ANALYST can, plus manage users/roles, create and edit detection rules, and configure the platform. |

Role is carried in the auth token and enforced at the API layer (from the auth phase onward).

## 2. Event taxonomy

Every security event is normalized into a common shape (defined in Phase 2) with a
`type`, severity, source identity (user / IP / asset), and timestamp. The initial catalog:

### 2.1 `FAILED_LOGIN`
- **What:** A single authentication attempt that failed (bad password, unknown user, expired credential).
- **Signals:** username, source IP, user agent, reason.
- **Severity:** Low on its own; meaningful in aggregate (see brute force).

### 2.2 `BRUTE_FORCE`
- **What:** Many failed logins for the same account or from the same IP within a short window.
- **Detection:** Threshold over a sliding time window (e.g. ≥ N `FAILED_LOGIN` in M minutes).
- **Severity:** High — strong signal of credential-guessing.

### 2.3 `SUSPICIOUS_LOGIN`
- **What:** A *successful* login that looks anomalous — new country/device, impossible travel
  (two logins too far apart in time vs. distance), or odd hour for that user.
- **Signals:** geo/IP, device fingerprint, time since last login location.
- **Severity:** Medium–High depending on confidence.

### 2.4 `API_ABUSE`
- **What:** A client calling APIs at abnormal volume or hitting error/forbidden responses
  repeatedly (scraping, enumeration, rate-limit evasion).
- **Signals:** endpoint, request rate, 4xx/5xx ratio, client identity.
- **Severity:** Medium — can indicate automation or probing.

### 2.5 `ABNORMAL_ACCESS`
- **What:** A user accessing resources outside their normal pattern — privilege escalation
  attempts, access to data they don't usually touch, or off-hours bulk access.
- **Signals:** resource, action, deviation from historical baseline.
- **Severity:** Medium–High — classic insider-threat / lateral-movement signal.

## 3. From events to incidents

Events are raw. The **detection** module applies rules to streams of events and, when a rule
fires, creates or updates an **incident** (grouping related events). The **risk** module rolls
events and incidents into risk scores per entity. This pipeline is built out in Phases 2+.
