package com.sentinelai.site.dto;

import com.sentinelai.site.domain.Site;
import com.sentinelai.site.domain.SiteStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Site API DTOs. */
public final class SiteDtos {

    private SiteDtos() {
    }

    public record CreateSiteRequest(@NotBlank @Size(max = 150) String name,
                                    @Size(max = 255) String domain) {
    }

    public record SiteResponse(Long id, String name, String domain, SiteStatus status,
                               Instant lastEventAt, boolean silenced, Instant createdAt) {
        public static SiteResponse from(Site s, boolean silenced) {
            return new SiteResponse(s.getId(), s.getName(), s.getDomain(), s.getStatus(),
                    s.getLastEventAt(), silenced, s.getCreatedAt());
        }
    }

    /** Returned once on create/rotate — the raw key is never stored or shown again. */
    public record ApiKeyResponse(Long keyId, Long siteId, String apiKey, String scope) {
    }

    public record CreatedSiteResponse(SiteResponse site, ApiKeyResponse apiKey) {
    }

    public record SnippetResponse(String curl, String node, String spring) {
    }
}
