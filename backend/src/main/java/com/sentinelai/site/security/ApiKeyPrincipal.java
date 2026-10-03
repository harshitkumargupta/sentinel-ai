package com.sentinelai.site.security;

/** Principal for a request authenticated by a per-site ingest API key. */
public record ApiKeyPrincipal(Long orgId, Long siteId) {
}
