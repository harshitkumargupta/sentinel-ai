# Deploy Status

A zero-cost public demo of SentinelAI: the production Docker Compose stack behind Caddy, exposed over
HTTPS by a Cloudflare quick tunnel. **No secrets are stored in this file or the repo** — the `.env`
is gitignored and the admin password is saved outside the repo at
`~/Desktop/sentinel-admin-password.txt`.

## Public URL

- **https://def-seemed-heel-arthritis.trycloudflare.com** _(quick tunnel restarted 2026-10-07; valid only while the current `cloudflared` runs)_
- Admin username: `socadmin` (password in `~/Desktop/sentinel-admin-password.txt`).
- Dev/seed logins (`admin`, `analyst`, `viewer`) are **disabled** on this stack (prod profile does not
  seed them) and return 401.

> Note: a Cloudflare **quick tunnel** gets a **new random URL every time it restarts**. The URL above
> is valid only while the current `cloudflared` process runs. For a stable URL, use a named tunnel
> with a Cloudflare account/domain (see `docs/deployment.md`).

## What's running

- Prod stack: `infrastructure/docker/docker-compose.prod.yml` (MySQL, Redis, backend, frontend, Caddy
  — only Caddy publishes a port: host **:80**). Images are local (`sentinel-ai-*:local`).
- Backend profile: `prod`. `KAFKA_ENABLED=false` (synchronous ingest). `AI_ENABLED=true` with the
  deterministic `fake` provider (no external LLM, works offline).
- One detection rule (BRUTE_FORCE) and a demo site were created via the API so the pipeline produces
  incidents; the admin bootstrap account is created from the `.env` on first start.

## Restart the tunnel

```bash
# If the tunnel died or you want a fresh URL:
cloudflared tunnel --url http://localhost:80
# copy the printed https://<random>.trycloudflare.com URL
```
The app stack does not need restarting for this — only the tunnel.

## Stop everything

```bash
# stop the tunnel
pkill -f "cloudflared tunnel"
# stop the app stack (keep data)
docker-compose --env-file infrastructure/docker/.env \
  -f infrastructure/docker/docker-compose.prod.yml down
# ...or also wipe the database volume:
docker-compose --env-file infrastructure/docker/.env \
  -f infrastructure/docker/docker-compose.prod.yml down -v
```

## Verified

- All containers healthy; `/api/health` 200 through the tunnel.
- Smoke test: admin login → create site + API key → ingest event via `X-API-Key` → fetch it →
  brute-force (6 failed logins) → **incident raised** (HIGH, OPEN).
- Security posture through the public URL: login page loads, API works, **Swagger UI, `/v3/api-docs`
  and `/actuator` are NOT reachable** (Caddy does not proxy them; actuator is internal-only for
  Prometheus), and security headers are present (CSP, `X-Frame-Options: DENY`, `nosniff`,
  Referrer/Permissions-Policy).

## Known limits

- **Quick-tunnel URL is ephemeral** — it changes whenever `cloudflared` restarts.
- **Single node, 8 GB RAM**: fine for a demo; the write path saturates under heavy concurrent load
  (reads are cache-served). Don't run the k6 ingest test against this instance.
- **Simulator disabled in prod** (admin-only "generate attacks" endpoint is off on a public instance);
  demo data was created via the normal API (rules + ingest).
- **AI is deterministic** (`fake` provider) — summaries are evidence-valid but not from a live LLM.
- **HTTP at the origin**; HTTPS/TLS is terminated at the Cloudflare edge, so `Strict-Transport-Security`
  is not set by the app (edge handles transport security).
- First boot requires a policy-compliant `ADMIN_PASSWORD` in `.env` (≥12 chars with upper/lower/digit/
  special) or the bootstrap fails fast.
