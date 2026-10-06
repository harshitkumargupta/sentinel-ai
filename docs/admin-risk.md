# SentinelAI — Multi-site & Admin-action Risk

## Multi-site tenancy

An org contains one or more **sites**. `security_events`, `incidents`, and `detection_rules` carry
a nullable `site_id` (existing rows backfilled to the Default Site). Users see sites they are granted
via `user_site_access`.

- **Ingest API keys** (`api_keys`): per-site, `scope=INGEST`, stored only as a SHA-256 hash. Create a
  site (`POST /api/sites`, ADMIN) and the raw key is returned **once**; rotate
  (`POST /api/sites/{id}/keys/rotate`) or revoke (`DELETE /api/sites/keys/{keyId}`).
- **Ingestion with `X-API-Key`:** the `ApiKeyAuthenticationFilter` resolves the key to its site and
  authenticates as `ROLE_INGEST` scoped to that site; events are tagged with the site and update its
  `last_event_at`. An invalid/revoked key → 401.
- **Onboarding:** `GET /api/sites/{id}/snippet` returns copy-paste curl/Node/Spring snippets; the UI
  shows **"connection verified ✓"** once the first event arrives. A site with no events for
  `sentinel.site.silence-minutes` is flagged **silent**.
- Site visibility is scoped per user (`user_site_access`); the React site selector lists accessible
  sites.

## Admin-action risk guard

Sensitive admin actions are scored (0–100) by composable `AdminActionRiskFactor`s over the actor's
baseline (`admin_baselines`): **Time** (off-hours), **NewIpCountryDevice**, **ActionSensitivity**,
**Burst**, **PrivilegeEscalation**, **PeerDeviation**, **UnusualSite**. Weights, thresholds and band
cutoffs live in `sentinel.admin-risk.*`. The score maps to a band, which drives the **adaptive
guard**:

| Band | Decision |
|---|---|
| LOW | **allow** |
| MEDIUM | **step-up** (re-authenticate), then allow |
| HIGH | **pending approval** by a *different* admin (`pending_admin_actions`); self-approval rejected; expires after `pending-expiry-minutes` |
| CRITICAL | **block** + revoke the actor's sessions + notify other admins |

Every decision is audited with before/after JSON and the risk breakdown. An `AdminRiskExplainer`
(template implementation now; AI-swappable later) produces the human-readable reason.

### Endpoints (ADMIN)

- `POST /api/admin/actions/disable-user/{id}` — risk-gated example action.
- `GET /api/admin/pending` · `POST /api/admin/pending/{id}/approve|reject` — approvals queue.
- `GET /api/admin/sessions/{userId}` · `POST /api/admin/sessions/{userId}/revoke` — session history + force-logout.
- `GET /api/admin/timeline?actorId=&action=&entityType=&days=` — admin activity timeline.

### Example

`disable-user` from a new IP, off-hours → sensitive(20) + new IP(10) + off-hours(15) = **45 /
MEDIUM → STEP_UP**; with step-up confirmed → **ALLOW**. A new-country + privilege-escalation variant
reaches **CRITICAL → BLOCKED** and the actor's sessions are revoked.
