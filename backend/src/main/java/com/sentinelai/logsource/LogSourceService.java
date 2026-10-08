package com.sentinelai.logsource;

import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.logsource.web.LogSourceDtos.CreateLogSourceRequest;
import com.sentinelai.logsource.web.LogSourceDtos.CreatedLogSource;
import com.sentinelai.logsource.web.LogSourceDtos.Health;
import com.sentinelai.logsource.web.LogSourceDtos.LogSourceView;
import com.sentinelai.site.SiteService;
import com.sentinelai.site.domain.ApiKey;
import com.sentinelai.site.domain.Site;
import com.sentinelai.site.domain.SiteStatus;
import com.sentinelai.site.domain.UserSiteAccess;
import com.sentinelai.site.dto.SiteDtos.ApiKeyResponse;
import com.sentinelai.site.repository.ApiKeyRepository;
import com.sentinelai.site.repository.SiteRepository;
import com.sentinelai.site.repository.UserSiteAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * QRadar-style log sources, modelled on sites: each source has a type, hashed ingest keys (raw key
 * shown once), an enabled flag (a disabled source's keys are refused at the auth filter), and live
 * health/volume figures. Every change is audited.
 */
@Service
@RequiredArgsConstructor
public class LogSourceService {

    /** The migration-created default site; it backs untagged data and can't be deleted. */
    static final long DEFAULT_SOURCE_ID = 1L;

    private final SiteRepository siteRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final UserSiteAccessRepository userSiteAccessRepository;
    private final OrganizationRepository organizationRepository;
    private final SiteService siteService;
    private final LogSourceStats stats;
    private final LogSourceProperties properties;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<LogSourceView> list(AppUserPrincipal actor) {
        Instant now = clock.instant();
        Map<Long, LogSourceStats.Volume> volumes =
                stats.volumes(actor.getOrgId(), now.minusSeconds(properties.getEpsWindowSeconds()));
        return siteRepository.findByOrg_IdOrderByIdAsc(actor.getOrgId()).stream()
                .filter(s -> actor.getRole() == Role.ADMIN || siteService.hasAccess(actor, s.getId()))
                .map(s -> view(s, volumes.get(s.getId()), now))
                .toList();
    }

    @Transactional
    public CreatedLogSource create(AppUserPrincipal actor, CreateLogSourceRequest req) {
        Site site = siteRepository.save(Site.builder()
                .org(organizationRepository.getReferenceById(actor.getOrgId()))
                .name(req.name().trim())
                .sourceType(req.type())
                .description(req.description() == null ? null : req.description().trim())
                .status(SiteStatus.ACTIVE)
                .build());
        userSiteAccessRepository.save(new UserSiteAccess(actor.getUserId(), site.getId()));
        ApiKeyResponse key = siteService.issueKey(site);
        audit(actor, "LOG_SOURCE_CREATE", site, "{\"type\":\"" + req.type() + "\"}");
        return new CreatedLogSource(view(site, null, clock.instant()), key);
    }

    @Transactional
    public LogSourceView setEnabled(AppUserPrincipal actor, Long id, boolean enabled) {
        Site site = load(actor, id);
        site.setStatus(enabled ? SiteStatus.ACTIVE : SiteStatus.DISABLED);
        siteRepository.save(site);
        audit(actor, enabled ? "LOG_SOURCE_ENABLE" : "LOG_SOURCE_DISABLE", site, "{}");
        return view(site, null, clock.instant());
    }

    @Transactional
    public ApiKeyResponse rotateKey(AppUserPrincipal actor, Long id) {
        Site site = load(actor, id);
        Instant now = clock.instant();
        for (ApiKey k : apiKeyRepository.findBySite_Id(id)) {
            if (k.isActive()) {
                k.setRevokedAt(now);
                apiKeyRepository.save(k);
            }
        }
        audit(actor, "LOG_SOURCE_ROTATE_KEY", site, "{}");
        return siteService.issueKey(site);
    }

    /** Deletes the source and its keys; its past events stay (their source becomes "unassigned"). */
    @Transactional
    public void delete(AppUserPrincipal actor, Long id) {
        Site site = load(actor, id);
        if (site.getId() == DEFAULT_SOURCE_ID) {
            throw new BadRequestException("The default log source cannot be deleted");
        }
        audit(actor, "LOG_SOURCE_DELETE", site, "{\"name\":\"" + site.getName().replace("\"", "'") + "\"}");
        siteRepository.delete(site);
    }

    /** Increment a source's parse-error counter (used by the ingest endpoints). */
    @Transactional
    public void recordParseErrors(Long siteId, long count) {
        if (siteId != null && count > 0) {
            siteRepository.addParseErrors(siteId, count);
        }
    }

    private LogSourceView view(Site s, LogSourceStats.Volume volume, Instant now) {
        long total = volume == null ? 0 : volume.total();
        double eps = volume == null ? 0 : (double) volume.recent() / properties.getEpsWindowSeconds();
        long activeKeys = apiKeyRepository.findBySite_Id(s.getId()).stream().filter(ApiKey::isActive).count();
        return new LogSourceView(s.getId(), s.getName(), s.getSourceType(), s.getDescription(),
                s.getStatus() == SiteStatus.ACTIVE, health(s, now), s.getLastEventAt(),
                Math.round(eps * 100.0) / 100.0, total, s.getParseErrorCount(), activeKeys, s.getCreatedAt());
    }

    private Health health(Site s, Instant now) {
        if (s.getStatus() == SiteStatus.DISABLED) {
            return Health.DISABLED;
        }
        if (s.getLastEventAt() == null) {
            return Health.NEVER;
        }
        return s.getLastEventAt().isAfter(now.minus(properties.getIdleAfterMinutes(), ChronoUnit.MINUTES))
                ? Health.RECEIVING : Health.IDLE;
    }

    private Site load(AppUserPrincipal actor, Long id) {
        return siteRepository.findById(id)
                .filter(s -> s.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Log source not found: " + id));
    }

    private void audit(AppUserPrincipal actor, String action, Site site, String details) {
        auditService.record(actor.getOrgId(), actor.getUserId(), action, "log_source", site.getId(), details, null);
    }
}
