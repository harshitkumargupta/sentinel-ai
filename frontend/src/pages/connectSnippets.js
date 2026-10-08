// Copy-paste instructions for "Connect My Website". Pure functions of (SentinelAI URL, key, site URL).
// The snippets send one JSON event per request: time, client IP, method, path, status, user agent.

export function agentCommand(base, key) {
  return `# on your web server (Python 3, no extra packages) — copy agent/sentinel_agent.py from the SentinelAI project
export SENTINEL_API_KEY='${key}'
python3 sentinel_agent.py --file /var/log/nginx/access.log --format ACCESS_LOG --url ${base}`;
}

export function curlTest(base, key) {
  return `curl -s -X POST ${base}/api/ingest/events \\
  -H 'Content-Type: application/json' -H 'X-API-Key: ${key}' \\
  -d '{"timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","sourceIp":"203.0.113.10","method":"GET","path":"/","status":200,"userAgent":"curl-test"}'`;
}

export function uploadSteps() {
  return `1. Download your server's access log (e.g. /var/log/nginx/access.log or Apache access_log).
2. Open Log Sources → Upload a log file, pick this site, format "ACCESS_LOG", choose the file → Upload.
3. Max size and line limits are shown on that page; bad lines are counted as parse errors, not ingested.`;
}

export function nodeSnippet(base, key) {
  return `// Express middleware — add before your routes. Node 18+ (built-in fetch).
const SENTINEL_URL = '${base}/api/ingest/events';
const SENTINEL_KEY = process.env.SENTINEL_API_KEY || '${key}';

app.use((req, res, next) => {
  res.on('finish', () => {
    fetch(SENTINEL_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-API-Key': SENTINEL_KEY },
      body: JSON.stringify({
        timestamp: new Date().toISOString(),
        sourceIp: (req.headers['x-forwarded-for'] || req.socket.remoteAddress || '').split(',')[0].trim(),
        method: req.method,
        path: req.originalUrl,
        status: res.statusCode,
        userAgent: req.headers['user-agent'] || '',
        username: req.body && typeof req.body.username === 'string' ? req.body.username : undefined,
      }),
      signal: AbortSignal.timeout(2000),
    }).catch(() => {}); // monitoring must never break the site
  });
  next();
});`;
}

export function springSnippet(base, key) {
  return `// Spring Boot 3 filter — drop into your app's package.
import jakarta.servlet.*; import jakarta.servlet.http.*;
import java.io.IOException; import java.net.URI; import java.net.http.*; import java.time.*; import java.util.Objects;
import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SentinelFilter extends OncePerRequestFilter {
    private static final String URL = "${base}/api/ingest/events";
    private final String key = System.getenv().getOrDefault("SENTINEL_API_KEY", "${key}");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(req, res);
        } finally {
            String path = req.getRequestURI() + (req.getQueryString() != null ? "?" + req.getQueryString() : "");
            String ua = Objects.toString(req.getHeader("User-Agent"), "");
            String json = String.format(
                "{\\"timestamp\\":\\"%s\\",\\"sourceIp\\":\\"%s\\",\\"method\\":\\"%s\\",\\"path\\":%s,\\"status\\":%d,\\"userAgent\\":%s}",
                Instant.now(), req.getRemoteAddr(), req.getMethod(), quote(path), res.getStatus(), quote(ua));
            http.sendAsync(HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json").header("X-API-Key", key)
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.discarding())
                .exceptionally(e -> null); // never break the site
        }
    }

    private static String quote(String s) {
        return "\\"" + s.replace("\\\\", "\\\\\\\\").replace("\\"", "\\\\\\"") + "\\"";
    }
}`;
}

export function flaskSnippet(base, key) {
  return `# Flask — add after creating \`app\`. Uses only the standard library + a background thread.
import json, os, threading, urllib.request
from datetime import datetime, timezone
from flask import request

SENTINEL_URL = "${base}/api/ingest/events"
SENTINEL_KEY = os.environ.get("SENTINEL_API_KEY", "${key}")

def _send(event):
    try:
        req = urllib.request.Request(SENTINEL_URL, data=json.dumps(event).encode(),
            headers={"Content-Type": "application/json", "X-API-Key": SENTINEL_KEY})
        urllib.request.urlopen(req, timeout=2).close()
    except Exception:
        pass  # monitoring must never break the site

@app.after_request
def sentinel_monitor(response):
    event = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "sourceIp": (request.headers.get("X-Forwarded-For") or request.remote_addr or "").split(",")[0].strip(),
        "method": request.method,
        "path": request.full_path.rstrip("?"),
        "status": response.status_code,
        "userAgent": request.headers.get("User-Agent", ""),
    }
    if request.form.get("username"):
        event["username"] = request.form["username"]
    threading.Thread(target=_send, args=(event,), daemon=True).start()
    return response`;
}

export function djangoSnippet(base, key) {
  return `# Django — save as yourapp/sentinel.py and add "yourapp.sentinel.SentinelMiddleware" to MIDDLEWARE.
import json, os, threading, urllib.request
from datetime import datetime, timezone

SENTINEL_URL = "${base}/api/ingest/events"
SENTINEL_KEY = os.environ.get("SENTINEL_API_KEY", "${key}")

def _send(event):
    try:
        req = urllib.request.Request(SENTINEL_URL, data=json.dumps(event).encode(),
            headers={"Content-Type": "application/json", "X-API-Key": SENTINEL_KEY})
        urllib.request.urlopen(req, timeout=2).close()
    except Exception:
        pass

class SentinelMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        response = self.get_response(request)
        event = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "sourceIp": (request.META.get("HTTP_X_FORWARDED_FOR") or request.META.get("REMOTE_ADDR", "")).split(",")[0].strip(),
            "method": request.method,
            "path": request.get_full_path(),
            "status": response.status_code,
            "userAgent": request.META.get("HTTP_USER_AGENT", ""),
        }
        if request.method == "POST" and request.POST.get("username"):
            event["username"] = request.POST["username"]
        threading.Thread(target=_send, args=(event,), daemon=True).start()
        return response`;
}

/** Owner-run checks against their OWN site. SentinelAI never sends these itself. */
export function testTraffic(siteUrl) {
  const u = (siteUrl || 'https://your-site.example').replace(/\/$/, '');
  return [
    { title: 'Repeated wrong logins → Brute force (10+ in 5 min for one username)',
      cmd: `for i in $(seq 1 12); do curl -s -o /dev/null -w "%{http_code}\\n" -X POST ${u}/login -d 'username=admin&password=wrong'; done`,
      note: 'Your login endpoint must answer 401/403 on a wrong password, and send the username field.' },
    { title: 'Requests for commonly probed paths → Abnormal access',
      cmd: `for p in /admin /.env /wp-login.php /.git/config /phpmyadmin; do curl -s -o /dev/null -w "$p %{http_code}\\n" ${u}$p; done` },
    { title: 'Quote-injection pattern in a URL parameter → SQL injection (3+ in 5 min)',
      cmd: `for i in 1 2 3; do curl -s -o /dev/null -w "%{http_code}\\n" "${u}/search?q=test%27%20OR%20%271%27%3D%271"; done`,
      note: 'Only a harmless test string in a query parameter; it does not exploit anything.' },
  ];
}
