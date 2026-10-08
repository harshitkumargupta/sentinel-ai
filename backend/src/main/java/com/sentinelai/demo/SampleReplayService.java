package com.sentinelai.demo;

import com.sentinelai.alert.domain.Alert;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.incident.repository.IncidentAlertRepository;
import com.sentinelai.ingestion.parse.LogIngestService;
import com.sentinelai.site.domain.Site;
import com.sentinelai.site.domain.SiteStatus;
import com.sentinelai.site.domain.UserSiteAccess;
import com.sentinelai.site.repository.SiteRepository;
import com.sentinelai.site.repository.UserSiteAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Demo Center "Replay": streams a bundled sample log file through the real parsers and pipeline. */
@Service
@RequiredArgsConstructor
public class SampleReplayService {

    private final LogIngestService logIngestService;
    private final SiteRepository siteRepository;
    private final UserSiteAccessRepository userSiteAccessRepository;
    private final OrganizationRepository organizationRepository;
    private final AlertRepository alertRepository;
    private final IncidentAlertRepository incidentAlertRepository;

    public record ReplayResult(String dataset, Long sourceId, String sourceName, LogIngestService.Report report,
                               int alertsCreated, List<String> rulesFired, List<Long> incidentIds) {
    }

    public List<SampleDatasets.Dataset> datasets() {
        return SampleDatasets.ALL;
    }

    /**
     * Stream a bundled sample file through the real parser + pipeline under its own log source,
     * re-timed to "now" and tagged so Reset Demo Data removes it.
     */
    public ReplayResult replay(Long orgId, Long actorId, String datasetId) {
        SampleDatasets.Dataset ds = SampleDatasets.find(datasetId)
                .orElseThrow(() -> new NotFoundException("Unknown dataset: " + datasetId));
        List<String> lines = readLines(ds.resource());
        Site source = siteRepository.findFirstByOrg_IdAndName(orgId, ds.sourceName())
                .orElseGet(() -> createSource(orgId, actorId, ds));
        String tag = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        LogIngestService.Report report = logIngestService.ingest(
                new LogIngestService.Request(orgId, source.getId(), ds.format(), lines, tag));
        List<Alert> alerts = alertRepository.findByRunId("replay-" + tag);
        List<Long> incidents = alerts.stream()
                .flatMap(a -> incidentAlertRepository.findById_AlertId(a.getId()).stream())
                .map(ia -> ia.getIncident().getId()).distinct().sorted().toList();
        return new ReplayResult(ds.id(), source.getId(), source.getName(), report, alerts.size(),
                alerts.stream().map(Alert::getRuleType).distinct().sorted().toList(), incidents);
    }

    private Site createSource(Long orgId, Long actorId, SampleDatasets.Dataset ds) {
        Site site = siteRepository.save(Site.builder()
                .org(organizationRepository.getReferenceById(orgId))
                .name(ds.sourceName()).sourceType(ds.sourceType())
                .description("Bundled sample dataset (" + ds.id() + ")")
                .status(SiteStatus.ACTIVE).build());
        if (actorId != null) {
            userSiteAccessRepository.save(new UserSiteAccess(actorId, site.getId()));
        }
        return site;
    }

    private static List<String> readLines(String resource) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new IllegalStateException("Bundled dataset missing: " + resource, e);
        }
    }

}
