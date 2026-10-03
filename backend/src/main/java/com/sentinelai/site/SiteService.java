package com.sentinelai.site;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.common.util.Hashing;
import com.sentinelai.site.domain.ApiKey;
import com.sentinelai.site.domain.Site;
import com.sentinelai.site.domain.SiteStatus;
import com.sentinelai.site.domain.UserSiteAccess;
import com.sentinelai.site.dto.SiteDtos.ApiKeyResponse;
import com.sentinelai.site.dto.SiteDtos.CreatedSiteResponse;
import com.sentinelai.site.dto.SiteDtos.SiteResponse;
import com.sentinelai.site.dto.SiteDtos.SnippetResponse;
import com.sentinelai.site.repository.ApiKeyRepository;
import com.sentinelai.site.repository.SiteRepository;
import com.sentinelai.site.repository.UserSiteAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SiteRepository siteRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final UserSiteAccessRepository userSiteAccessRepository;
    private final OrganizationRepository organizationRepository;
    private final SiteProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${sentinel.ingest-base-url:http://localhost:8080}")
    private String ingestBaseUrl;

    @Transactional(readOnly = true)
    public List<SiteResponse> listAccessible(AppUserPrincipal actor) {
        return siteRepository.findByOrg_IdOrderByIdAsc(actor.getOrgId()).stream()
                .filter(s -> hasAccess(actor, s.getId()))
                .map(s -> SiteResponse.from(s, isSilenced(s)))
                .toList();
    }

    public boolean hasAccess(AppUserPrincipal actor, Long siteId) {
        return userSiteAccessRepository.existsById_UserIdAndId_SiteId(actor.getUserId(), siteId);
    }

    @Transactional
    public CreatedSiteResponse create(AppUserPrincipal actor, String name, String domain) {
        Site site = siteRepository.save(Site.builder()
                .org(organizationRepository.getReferenceById(actor.getOrgId()))
                .name(name).domain(domain).status(SiteStatus.ACTIVE)
                .build());
        userSiteAccessRepository.save(new UserSiteAccess(actor.getUserId(), site.getId()));
        ApiKeyResponse key = issueKey(site);
        return new CreatedSiteResponse(SiteResponse.from(site, isSilenced(site)), key);
    }

    @Transactional
    public ApiKeyResponse rotateKey(AppUserPrincipal actor, Long siteId) {
        Site site = requireAccessibleSite(actor, siteId);
        Instant now = clock.instant();
        for (ApiKey existing : apiKeyRepository.findBySite_Id(siteId)) {
            if (existing.isActive()) {
                existing.setRevokedAt(now);
                apiKeyRepository.save(existing);
            }
        }
        return issueKey(site);
    }

    @Transactional
    public void revokeKey(AppUserPrincipal actor, Long keyId) {
        ApiKey key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new NotFoundException("API key not found: " + keyId));
        requireAccessibleSite(actor, key.getSite().getId());
        key.setRevokedAt(clock.instant());
        apiKeyRepository.save(key);
    }

    @Transactional(readOnly = true)
    public SnippetResponse snippet(AppUserPrincipal actor, Long siteId) {
        requireAccessibleSite(actor, siteId);
        String url = ingestBaseUrl + "/api/events/ingest";
        String curl = "curl -X POST " + url + " \\\n"
                + "  -H 'X-API-Key: <API_KEY>' -H 'Content-Type: application/json' \\\n"
                + "  -d '{\"payload\":{\"eventType\":\"FAILED_LOGIN\",\"severity\":\"LOW\",\"username\":\"jdoe\",\"sourceIp\":\"203.0.113.5\"}}'";
        String node = "await fetch('" + url + "', {\n"
                + "  method: 'POST',\n"
                + "  headers: { 'X-API-Key': process.env.SENTINEL_API_KEY, 'Content-Type': 'application/json' },\n"
                + "  body: JSON.stringify({ payload: { eventType: 'FAILED_LOGIN', severity: 'LOW', username: 'jdoe', sourceIp: '203.0.113.5' } })\n"
                + "});";
        String spring = "webClient.post().uri(\"" + url + "\")\n"
                + "  .header(\"X-API-Key\", apiKey)\n"
                + "  .bodyValue(Map.of(\"payload\", Map.of(\"eventType\", \"FAILED_LOGIN\", \"severity\", \"LOW\")))\n"
                + "  .retrieve().toBodilessEntity().block();";
        return new SnippetResponse(curl, node, spring);
    }

    private ApiKeyResponse issueKey(Site site) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String raw = "sk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ApiKey key = apiKeyRepository.save(ApiKey.builder()
                .site(site).keyHash(Hashing.sha256Hex(raw)).scope("INGEST").build());
        return new ApiKeyResponse(key.getId(), site.getId(), raw, key.getScope());
    }

    private Site requireAccessibleSite(AppUserPrincipal actor, Long siteId) {
        Site site = siteRepository.findById(siteId)
                .filter(s -> s.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Site not found: " + siteId));
        if (!hasAccess(actor, siteId)) {
            throw new NotFoundException("Site not found: " + siteId);
        }
        return site;
    }

    private boolean isSilenced(Site site) {
        return site.getLastEventAt() != null
                && site.getLastEventAt().isBefore(clock.instant().minus(properties.getSilenceMinutes(), ChronoUnit.MINUTES));
    }
}
