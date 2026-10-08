#!/usr/bin/env python3
"""SentinelAI log-shipping agent.

Tails a log file (like `tail -F`: follows rotation and truncation) and ships new lines in batches to
SentinelAI's `POST /api/ingest/raw` using a log source's ingest key. Python 3.8+ standard library
only — no installs.

    export SENTINEL_API_KEY=sk_...            # the key shown once when you created the log source
    python3 sentinel_agent.py --file /var/log/nginx/access.log --url http://localhost:8088 --format ACCESS_LOG

--format is optional: without it the server uses the log source's type (web server → ACCESS_LOG,
auth → AUTH_LOG, firewall → CSV, application/generic → JSON_LINES). For CSV, the first line shipped
must be the header row, so start CSV files with --from-start.
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request

FORMATS = ("ACCESS_LOG", "AUTH_LOG", "JSON_LINES", "CSV")


def parse_args(argv=None):
    p = argparse.ArgumentParser(description="Tail a log file and ship it to SentinelAI.")
    p.add_argument("--file", required=True, help="log file to follow")
    p.add_argument("--url", default=os.environ.get("SENTINEL_URL", "http://localhost:8088"),
                   help="SentinelAI base URL (default: $SENTINEL_URL or http://localhost:8088)")
    p.add_argument("--format", choices=FORMATS, help="log format (default: the source's type)")
    p.add_argument("--key-env", default="SENTINEL_API_KEY", help="env var holding the ingest key")
    p.add_argument("--batch-size", type=int, default=200, help="max lines per request (default 200)")
    p.add_argument("--flush-seconds", type=float, default=2.0, help="ship at least this often (default 2s)")
    p.add_argument("--from-start", action="store_true", help="ship the existing file content first")
    p.add_argument("--once", action="store_true", help="ship what is there now, then exit (no follow)")
    return p.parse_args(argv)


def post(url, key, fmt, lines, max_attempts=5):
    """POST one batch; retries with exponential backoff on network errors, 429 and 5xx."""
    body = {"lines": lines}
    if fmt:
        body["format"] = fmt
    data = json.dumps(body).encode("utf-8")
    delay = 1.0
    for attempt in range(1, max_attempts + 1):
        req = urllib.request.Request(url.rstrip("/") + "/api/ingest/raw", data=data, method="POST",
                                     headers={"Content-Type": "application/json", "X-API-Key": key})
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                report = json.loads(resp.read().decode("utf-8")).get("data", {})
                log(f"shipped {len(lines)} line(s): accepted={report.get('accepted')} "
                    f"skipped={report.get('skipped')} parseErrors={report.get('parseErrors')}")
                return True
        except urllib.error.HTTPError as e:
            if e.code in (401, 403):
                log(f"rejected by server ({e.code}): check the key and that the log source is enabled")
                return False
            if e.code == 400:
                log(f"batch refused (400): {e.read().decode('utf-8', 'replace')[:300]}")
                return False
            if e.code == 429:
                delay = max(delay, float(e.headers.get("Retry-After", delay)))
            log(f"server error {e.code}, attempt {attempt}/{max_attempts}")
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            log(f"network error ({e}), attempt {attempt}/{max_attempts}")
        time.sleep(delay)
        delay = min(delay * 2, 30)
    log("giving up on this batch after retries")
    return False


def follow(path, from_start, once):
    """Yield lines as they are appended; reopen on rotation (inode change) or truncation."""
    f, inode = None, None
    pending = ""
    while True:
        if f is None:
            try:
                f = open(path, "r", encoding="utf-8", errors="replace")
                inode = os.fstat(f.fileno()).st_ino
                if not from_start:
                    f.seek(0, os.SEEK_END)
                from_start = True  # files opened after a rotation are read from their start
            except FileNotFoundError:
                if once:
                    return
                time.sleep(1)
                yield None
                continue
        chunk = f.readline()
        if chunk:
            pending += chunk
            if pending.endswith("\n"):
                yield pending.rstrip("\r\n")
                pending = ""
            continue
        if once:
            if pending:
                yield pending
            return
        try:
            st = os.stat(path)
            if st.st_ino != inode or st.st_size < f.tell():
                f.close()
                f = None  # rotated or truncated: reopen
                continue
        except FileNotFoundError:
            f.close()
            f = None
            continue
        yield None  # idle tick so the caller can flush on time
        time.sleep(0.5)


def log(msg):
    print(time.strftime("%H:%M:%S"), "[sentinel-agent]", msg, file=sys.stderr, flush=True)


def main(argv=None):
    args = parse_args(argv)
    key = os.environ.get(args.key_env)
    if not key:
        log(f"set ${args.key_env} to the log source's ingest key")
        return 2
    batch, last_flush = [], time.monotonic()
    log(f"following {args.file} -> {args.url} (format: {args.format or 'source default'})")
    try:
        for line in follow(args.file, args.from_start, args.once):
            if line is not None and line.strip():
                batch.append(line)
            due = time.monotonic() - last_flush >= args.flush_seconds
            if batch and (len(batch) >= args.batch_size or due):
                post(args.url, key, args.format, batch)
                batch, last_flush = [], time.monotonic()
            elif due:
                last_flush = time.monotonic()
        if batch:
            post(args.url, key, args.format, batch)
    except KeyboardInterrupt:
        if batch:
            post(args.url, key, args.format, batch)
        log("stopped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
