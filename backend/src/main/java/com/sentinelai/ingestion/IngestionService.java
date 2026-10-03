package com.sentinelai.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.event.SecurityEventCreatedEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.ingestion.enrich.GeoIpEnricher;
import com.sentinelai.ingestion.normalize.EventNormalizer;
import com.sentinelai.ingestion.normalize.NormalizedEvent;
import com.sentinelai.site.domain.Site;
import com.sentinelai.site.repository.SiteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Single ingestion path for both the REST API and the simulator: normalize → validate/cap →
 * enrich (GeoIP, non-blocking) → derive entity key → dedupe (optional client event id) → persist →
 * publish for detection.
 */
@Slf4j
@Service
public class IngestionService {

    public record IngestOutcome(Long eventId, boolean duplicate) {
    }

    private final Map<String, EventNormalizer> normalizers;
    private final SecurityEventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final SiteRepository siteRepository;
    private final GeoIpEnricher geoIpEnricher;
    private final IngestionProperties properties;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public IngestionService(List<EventNormalizer> normalizerBeans,
                            SecurityEventRepository eventRepository,
                            OrganizationRepository organizationRepository,
                            SiteRepository siteRepository,
                            GeoIpEnricher geoIpEnricher,
                            IngestionProperties properties,
                            ApplicationEventPublisher publisher,
                            Clock clock) {
        this.normalizers = normalizerBeans.stream()
                .collect(Collectors.toMap(EventNormalizer::sourceType, Function.identity()));
        this.eventRepository = eventRepository;
        this.organizationRepository = organizationRepository;
        this.siteRepository = siteRepository;
        this.geoIpEnricher = geoIpEnricher;
        this.properties = properties;
        this.publisher = publisher;
        this.clock = clock;
    }

    public int maxBatchSize() {
        return properties.getMaxBatchSize();
    }

    @Transactional
    public IngestOutcome ingest(Long orgId, Long siteId, String sourceType, JsonNode payload,
                                String clientEventIdOverride) {
        if (payload == null || payload.isNull()) {
            throw new BadRequestException("payload is required");
        }
        EventNormalizer normalizer = normalizers.getOrDefault(
                sourceType == null ? "generic" : sourceType, normalizers.get("generic"));
        NormalizedEvent n = normalizer.normalize(payload);

        String clientEventId = clientEventIdOverride != null ? clientEventIdOverride : n.getClientEventId();
        if (clientEventId != null) {
            Optional<SecurityEvent> existing =
                    eventRepository.findByOrg_IdAndClientEventId(orgId, clientEventId);
            if (existing.isPresent()) {
                return new IngestOutcome(existing.get().getId(), true);
            }
        }

        String rawPayload = n.getRawPayload();
        if (rawPayload != null
                && rawPayload.getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) {
            throw new BadRequestException("payload exceeds max size of " + properties.getMaxPayloadBytes() + " bytes");
        }

        Instant timestamp = n.getEventTimestamp() != null ? n.getEventTimestamp() : clock.instant();
        String geoCountry = n.getGeoCountry();
        if (geoCountry == null && n.getSourceIp() != null) {
            geoCountry = geoIpEnricher.country(n.getSourceIp()).orElse(null); // never blocks
        }
        String entityKey = n.getEntityKey() != null ? n.getEntityKey() : deriveEntityKey(n);

        Site site = null;
        if (siteId != null) {
            site = siteRepository.getReferenceById(siteId);
            site.setLastEventAt(timestamp); // for site-silence detection
        }

        SecurityEvent event = eventRepository.save(SecurityEvent.builder()
                .org(organizationRepository.getReferenceById(orgId))
                .site(site)
                .clientEventId(clientEventId)
                .eventType(n.getEventType())
                .severity(n.getSeverity())
                .sourceIp(n.getSourceIp())
                .username(n.getUsername())
                .userAgent(n.getUserAgent())
                .resource(n.getResource())
                .assetCriticality(n.getAssetCriticality())
                .rawPayload(rawPayload)
                .geoCountry(geoCountry)
                .geoCity(n.getGeoCity())
                .honeytoken(n.isHoneytoken())
                .entityKey(entityKey)
                .correlationKey(n.getCorrelationKey())
                .eventTimestamp(timestamp)
                .build());

        publisher.publishEvent(new SecurityEventCreatedEvent(event.getId(), orgId));
        return new IngestOutcome(event.getId(), false);
    }

    private String deriveEntityKey(NormalizedEvent n) {
        if (n.getUsername() != null) {
            return "user:" + n.getUsername();
        }
        if (n.getSourceIp() != null) {
            return "ip:" + n.getSourceIp();
        }
        return null;
    }
}
