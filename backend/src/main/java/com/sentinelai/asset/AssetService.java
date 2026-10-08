package com.sentinelai.asset;

import com.sentinelai.asset.web.AssetDtos.AssetDetail;
import com.sentinelai.asset.web.AssetDtos.AssetView;
import com.sentinelai.asset.web.AssetDtos.ImportResult;
import com.sentinelai.asset.web.AssetDtos.IncidentRef;
import com.sentinelai.asset.web.AssetDtos.SaveAssetRequest;
import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.ConflictException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.detection.buildingblock.Cidr;
import com.sentinelai.ingestion.parse.CsvParser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Asset inventory: CRUD, CSV import (upsert by hostname or IP), and relinking stored events to assets
 * (for assets added after their events arrived). New events are linked at ingest by AssetResolver.
 */
@Service
@RequiredArgsConstructor
public class AssetService {

    static final int MAX_IMPORT_ROWS = 5000;

    private final AssetRepository repository;
    private final OrganizationRepository organizationRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;
    private final AssetVulnerabilityCounter vulnerabilityCounter;

    @Transactional(readOnly = true)
    public List<AssetView> list(AppUserPrincipal actor) {
        Map<Long, long[]> stats = stats(actor.getOrgId());
        Map<Long, Long> vulns = vulnerabilityCounter.openByAsset(actor.getOrgId());
        return repository.findByOrg_IdOrderByCriticalityDescHostnameAsc(actor.getOrgId()).stream()
                .map(a -> {
                    long[] s = stats.getOrDefault(a.getId(), new long[2]);
                    return AssetView.of(a, s[0], s[1], vulns.getOrDefault(a.getId(), 0L));
                }).toList();
    }

    @Transactional(readOnly = true)
    public AssetDetail get(Long id, AppUserPrincipal actor) {
        Asset a = load(id, actor);
        long[] s = stats(actor.getOrgId()).getOrDefault(id, new long[2]);
        List<IncidentRef> incidents = jdbc.query("""
                select distinct i.id, i.title, i.status, i.severity, i.risk_score from incidents i
                join incident_events ie on ie.incident_id = i.id
                join security_events e on e.id = ie.event_id
                where e.asset_id = :asset and i.org_id = :org order by i.id desc limit 100
                """, new MapSqlParameterSource("asset", id).addValue("org", actor.getOrgId()),
                (rs, n) -> new IncidentRef(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        (Integer) rs.getObject(5)));
        return new AssetDetail(AssetView.of(a, s[0], s[1],
                vulnerabilityCounter.openByAsset(actor.getOrgId()).getOrDefault(id, 0L)), incidents);
    }

    @Transactional
    public AssetView save(Long id, SaveAssetRequest req, AppUserPrincipal actor) {
        String hostname = blank(req.hostname());
        String ip = blank(req.ip());
        if (hostname == null && ip == null) {
            throw new BadRequestException("An asset needs a hostname or an IP");
        }
        if (ip != null && !Cidr.isIpv4(ip)) {
            throw new BadRequestException("IP must be an IPv4 address");
        }
        Asset a = id == null ? new Asset() : load(id, actor);
        repository.findByOrg_IdAndHostnameIgnoreCase(actor.getOrgId(), hostname == null ? "\u0000" : hostname)
                .filter(o -> !o.getId().equals(a.getId()))
                .ifPresent(o -> { throw new ConflictException("Another asset already has hostname " + hostname); });
        repository.findByOrg_IdAndIp(actor.getOrgId(), ip == null ? "\u0000" : ip)
                .filter(o -> !o.getId().equals(a.getId()))
                .ifPresent(o -> { throw new ConflictException("Another asset already has IP " + ip); });
        a.setOrg(organizationRepository.getReferenceById(actor.getOrgId()));
        a.setHostname(hostname);
        a.setIp(ip);
        a.setOwner(blank(req.owner()));
        a.setType(req.type());
        a.setEnvironment(req.environment());
        a.setCriticality(req.criticality());
        a.setDescription(blank(req.description()));
        Asset saved = repository.save(a);
        relink(actor.getOrgId());
        auditService.record(actor.getOrgId(), actor.getUserId(), id == null ? "ASSET_CREATE" : "ASSET_UPDATE",
                "asset", saved.getId(), "{\"criticality\":\"" + saved.getCriticality() + "\"}", null);
        return AssetView.of(saved, 0, 0, 0);
    }

    @Transactional
    public void delete(Long id, AppUserPrincipal actor) {
        Asset a = load(id, actor);
        repository.delete(a); // events keep their data; asset_id becomes NULL
        auditService.record(actor.getOrgId(), actor.getUserId(), "ASSET_DELETE", "asset", id, "{}", null);
    }

    /**
     * Import a CSV with a header row; columns (any order, case-insensitive): hostname, ip, owner, type,
     * environment, criticality, description. Rows upsert by hostname, else IP. Bad rows are reported,
     * never fatal.
     */
    @Transactional
    public ImportResult importCsv(List<String> lines, AppUserPrincipal actor) {
        List<String> rows = lines.stream().map(l -> l.replace("﻿", "")).filter(l -> !l.isBlank()).toList();
        if (rows.isEmpty()) {
            throw new BadRequestException("The file is empty");
        }
        if (rows.size() - 1 > MAX_IMPORT_ROWS) {
            throw new BadRequestException("Too many rows (max " + MAX_IMPORT_ROWS + ")");
        }
        List<String> header;
        try {
            header = CsvParser.split(rows.get(0)).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        } catch (RuntimeException e) {
            throw new BadRequestException("Unreadable CSV header: " + e.getMessage());
        }
        if (!header.contains("hostname") && !header.contains("ip")) {
            throw new BadRequestException("Header must include a 'hostname' or 'ip' column");
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            try {
                List<String> cells = CsvParser.split(rows.get(i));
                Map<String, String> row = new HashMap<>();
                for (int c = 0; c < header.size() && c < cells.size(); c++) {
                    row.put(header.get(c), cells.get(c).trim());
                }
                String hostname = blank(row.get("hostname"));
                String ip = blank(row.get("ip"));
                if (hostname == null && ip == null) {
                    skipped++;
                    continue;
                }
                if (hostname != null && !hostname.matches("^[A-Za-z0-9._\\-]{1,255}$")) {
                    throw new IllegalArgumentException("invalid hostname");
                }
                if (ip != null && !Cidr.isIpv4(ip)) {
                    throw new IllegalArgumentException("invalid IPv4");
                }
                Asset a = (hostname != null ? repository.findByOrg_IdAndHostnameIgnoreCase(actor.getOrgId(), hostname)
                        : repository.findByOrg_IdAndIp(actor.getOrgId(), ip)).orElse(null);
                Asset current = a;
                if (ip != null) {
                    // Pre-check the unique IP so a clash is a row error, not a rolled-back import.
                    repository.findByOrg_IdAndIp(actor.getOrgId(), ip)
                            .filter(o -> current == null || !o.getId().equals(current.getId()))
                            .ifPresent(o -> { throw new IllegalArgumentException("IP " + ip + " belongs to " + o.label()); });
                }
                boolean isNew = a == null;
                if (isNew) {
                    a = Asset.builder().org(organizationRepository.getReferenceById(actor.getOrgId())).build();
                }
                a.setHostname(hostname != null ? hostname : a.getHostname());
                a.setIp(ip != null ? ip : a.getIp());
                a.setOwner(blank(row.getOrDefault("owner", a.getOwner())));
                a.setType(parse(AssetType.class, row.get("type"), a.getType() != null ? a.getType() : AssetType.OTHER));
                a.setEnvironment(parse(AssetEnvironment.class, row.get("environment"),
                        a.getEnvironment() != null ? a.getEnvironment() : AssetEnvironment.CORPORATE));
                a.setCriticality(parse(AssetCriticality.class, row.get("criticality"),
                        a.getCriticality() != null ? a.getCriticality() : AssetCriticality.MEDIUM));
                a.setDescription(blank(row.getOrDefault("description", a.getDescription())));
                repository.saveAndFlush(a);
                if (isNew) {
                    created++;
                } else {
                    updated++;
                }
            } catch (RuntimeException e) {
                if (errors.size() < 20) {
                    errors.add("row " + (i + 1) + ": " + (e.getMessage() == null ? "invalid" : e.getMessage()));
                }
            }
        }
        int relinked = relink(actor.getOrgId());
        auditService.record(actor.getOrgId(), actor.getUserId(), "ASSET_IMPORT", "asset", null,
                "{\"created\":" + created + ",\"updated\":" + updated + "}", null);
        return new ImportResult(created, updated, skipped, errors, relinked);
    }

    /**
     * Link stored, unlinked events of the org to assets (host entity key, then source IP) and fill
     * their criticality if the source didn't. Returns the number of events linked. Existing incident
     * risk scores are recomputed only when those incidents next receive an alert.
     */
    @Transactional
    public int relink(Long orgId) {
        MapSqlParameterSource p = new MapSqlParameterSource("org", orgId);
        int byHost = jdbc.update("""
                update security_events e join assets a
                  on a.org_id = e.org_id and e.entity_key like 'host:%' and lower(a.hostname) = lower(substring(e.entity_key, 6))
                set e.asset_id = a.id,
                    e.asset_criticality = coalesce(e.asset_criticality, case a.criticality
                        when 'LOW' then 1 when 'MEDIUM' then 2 when 'HIGH' then 3 else 4 end)
                where e.org_id = :org and e.asset_id is null
                """, p);
        int byIp = jdbc.update("""
                update security_events e join assets a on a.org_id = e.org_id and a.ip = e.source_ip
                set e.asset_id = a.id,
                    e.asset_criticality = coalesce(e.asset_criticality, case a.criticality
                        when 'LOW' then 1 when 'MEDIUM' then 2 when 'HIGH' then 3 else 4 end)
                where e.org_id = :org and e.asset_id is null
                """, p);
        return byHost + byIp;
    }

    /** asset id → {linked events, open incidents}. */
    private Map<Long, long[]> stats(Long orgId) {
        Map<Long, long[]> out = new HashMap<>();
        jdbc.query("""
                select e.asset_id, count(distinct e.id),
                       count(distinct case when i.status not in ('RESOLVED','CLOSED','FALSE_POSITIVE') then i.id end)
                from security_events e
                left join incident_events ie on ie.event_id = e.id
                left join incidents i on i.id = ie.incident_id
                where e.org_id = :org and e.asset_id is not null group by e.asset_id
                """, new MapSqlParameterSource("org", orgId),
                rs -> { out.put(rs.getLong(1), new long[]{rs.getLong(2), rs.getLong(3)}); });
        return out;
    }

    private Asset load(Long id, AppUserPrincipal actor) {
        return repository.findById(id).filter(a -> a.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Asset not found: " + id));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, E def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid " + type.getSimpleName().replace("Asset", "").toLowerCase()
                    + " '" + raw + "'");
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
