package com.sentinelai.site;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.site.domain.LogSourceType;
import com.sentinelai.site.domain.Site;
import com.sentinelai.site.dto.SiteDtos.CreatedSiteResponse;
import com.sentinelai.site.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * "Connect My Website": registers the owner's site (with an explicit authorization attestation,
 * audited), issues its ingest key once (stored hashed), reports whether events have arrived, and
 * runs an on-demand availability check (one plain GET of the registered URL — never attack traffic).
 */
@Service
@RequiredArgsConstructor
public class SiteConnectService {

    public enum Method { AGENT, LOG_UPLOAD, MIDDLEWARE, MONITOR }

    static final Duration MONITOR_TIMEOUT = Duration.ofSeconds(5);

    public record LastEvent(Long id, String type, String sourceIp, String resource, Instant time) {
    }

    public record Connection(Long siteId, boolean connected, long eventCount, Instant lastEventAt, LastEvent lastEvent) {
    }

    public record MonitorResult(String url, boolean reachable, Integer status, long latencyMs, String error, Long eventId) {
    }

    private final SiteService sites;
    private final SiteRepository siteRepository;
    private final AuditService audit;
    private final IngestionService ingestion;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public CreatedSiteResponse connect(AppUserPrincipal actor, String name, String url, boolean authorized, Method method) {
        if (!authorized) {
            throw new BadRequestException("Confirm that you own or are authorized to monitor this site");
        }
        URI uri = parseUrl(url);
        CreatedSiteResponse created = sites.create(actor, name.trim(), uri.toString());
        siteRepository.findById(created.site().id()).ifPresent(s -> {
            s.setSourceType(LogSourceType.WEB_SERVER);
            s.setDescription("Connected via wizard (" + method + ")");
        });
        ObjectNode details = objectMapper.createObjectNode().put("url", uri.toString()).put("method", method.name())
                .put("authorizedByOwner", true).put("keyId", created.apiKey().keyId());
        audit.record(actor.getOrgId(), actor.getUserId(), "SITE_CONNECT", "site", created.site().id(), details.toString(), null);
        return created;
    }

    @Transactional(readOnly = true)
    public Connection connection(AppUserPrincipal actor, Long siteId) {
        sites.requireAccessibleSite(actor, siteId);
        MapSqlParameterSource p = new MapSqlParameterSource("site", siteId).addValue("org", actor.getOrgId());
        Long n = jdbc.queryForObject("select count(*) from security_events where org_id = :org and site_id = :site", p, Long.class);
        Timestamp last0 = jdbc.queryForObject(
                "select max(ingested_at) from security_events where org_id = :org and site_id = :site", p, Timestamp.class);
        Instant lastAt = last0 == null ? null : last0.toInstant();
        List<LastEvent> last = jdbc.query("""
                select id, event_type, source_ip, resource, event_timestamp from security_events
                where org_id = :org and site_id = :site order by id desc limit 1
                """, p, (rs, i) -> new LastEvent(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getTimestamp(5).toInstant()));
        return new Connection(siteId, n != null && n > 0, n == null ? 0 : n, lastAt, last.isEmpty() ? null : last.get(0));
    }

    /** One GET of the registered URL (no redirects followed, short timeout), recorded as an event for the site. */
    public MonitorResult monitorCheck(AppUserPrincipal actor, Long siteId) {
        Site site = sites.requireAccessibleSite(actor, siteId);
        URI uri = parseUrl(site.getDomain());
        long start = clock.millis();
        Integer status = null;
        String error = null;
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(MONITOR_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<Void> res = client.send(HttpRequest.newBuilder(uri).timeout(MONITOR_TIMEOUT)
                    .header("User-Agent", "SentinelAI-Monitor/1.0").GET().build(), HttpResponse.BodyHandlers.discarding());
            status = res.statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = "interrupted";
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
        }
        long latency = clock.millis() - start;
        boolean up = status != null && status < 500;
        ObjectNode payload = objectMapper.createObjectNode().put("eventType", "OTHER")
                .put("severity", up ? "LOW" : "MEDIUM").put("outcome", up ? "SUCCESS" : "FAILURE")
                .put("resource", "monitor:" + truncate(uri.toString(), 200)).put("userAgent", "SentinelAI-Monitor/1.0")
                .put("latencyMs", latency);
        if (status != null) {
            payload.put("status", status);
        }
        if (error != null) {
            payload.put("error", truncate(error, 200));
        }
        Long eventId = ingestion.ingest(actor.getOrgId(), siteId, "generic", payload, null).eventId();
        return new MonitorResult(uri.toString(), up, status, latency, error == null ? null : truncate(error, 200), eventId);
    }

    static URI parseUrl(String url) {
        try {
            URI uri = URI.create(url == null ? "" : url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Enter a valid http(s) URL, e.g. https://shop.example.com");
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
