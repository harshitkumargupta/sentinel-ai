# SentinelAI agent

A small log shipper: it follows a log file (like `tail -F`, surviving rotation and truncation) and sends
new lines in batches to SentinelAI's `POST /api/ingest/raw` with a log source's ingest key. The server
parses them (nginx/Apache access log, Linux `auth.log`, JSON lines, CSV) and feeds the normal detection
pipeline. Python 3.8+ standard library only.

## 1. Create a log source

In SentinelAI go to **Log Sources → Add a log source**, pick the type (e.g. *Web server*), and copy the
API key. It is shown **once**; the server stores only its SHA-256 hash.

## 2. Run the agent

```bash
export SENTINEL_API_KEY='sk_...'        # the key from step 1 — keep it out of shell history/scripts
python3 agent/sentinel_agent.py \
  --file /var/log/nginx/access.log \
  --url http://localhost:8088           # the SentinelAI URL (demo stack default)
```

| Option | Meaning |
|---|---|
| `--format ACCESS_LOG\|AUTH_LOG\|JSON_LINES\|CSV` | Override the parser (default: the log source's type) |
| `--from-start` | Ship what is already in the file first (needed for CSV so the header goes first) |
| `--once` | Ship the current content and exit (good for a quick test) |
| `--batch-size 200` / `--flush-seconds 2` | Batching: up to N lines, at least every S seconds |

Quick test with a bundled sample:

```bash
python3 agent/sentinel_agent.py --file backend/src/main/resources/samples/auth.log --format AUTH_LOG --from-start --once
```

The **Log Sources** page then shows the source as *receiving*, with its events/sec, total events and
parse errors (lines the parser could not read).

## Behaviour

- Retries network errors, HTTP 429 (honouring `Retry-After`) and 5xx with exponential backoff (max 30s).
- Stops sending a batch on 400 (malformed request) and on 401/403 (bad key or disabled log source),
  and logs why.
- Keeps no state on disk; on restart it starts from the end of the file unless `--from-start`.

## Copy-paste snippet (curl)

```bash
curl -X POST http://localhost:8088/api/ingest/raw \
  -H "X-API-Key: $SENTINEL_API_KEY" -H 'Content-Type: application/json' \
  -d '{"format":"AUTH_LOG","lines":["Oct  8 10:00:01 web-01 sshd[1]: Failed password for alice from 203.0.113.5 port 22 ssh2"]}'
```
