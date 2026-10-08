package com.sentinelai.asset.web;

import com.sentinelai.asset.Asset;
import com.sentinelai.asset.AssetCriticality;
import com.sentinelai.asset.AssetEnvironment;
import com.sentinelai.asset.AssetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Asset API DTOs. */
public final class AssetDtos {

    private AssetDtos() {
    }

    public record SaveAssetRequest(
            @Size(max = 255) @Pattern(regexp = "^[A-Za-z0-9._\\-]*$", message = "letters, digits, '.', '_' and '-' only") String hostname,
            @Size(max = 45) String ip,
            @Size(max = 150) String owner,
            @NotNull AssetType type,
            @NotNull AssetEnvironment environment,
            @NotNull AssetCriticality criticality,
            @Size(max = 500) String description) {
    }

    public record AssetView(Long id, String hostname, String ip, String owner, AssetType type,
                            AssetEnvironment environment, AssetCriticality criticality, String description,
                            long linkedEvents, long openIncidents, long openVulnerabilities, Instant updatedAt) {
        public static AssetView of(Asset a, long events, long incidents, long vulns) {
            return new AssetView(a.getId(), a.getHostname(), a.getIp(), a.getOwner(), a.getType(), a.getEnvironment(),
                    a.getCriticality(), a.getDescription(), events, incidents, vulns, a.getUpdatedAt());
        }
    }

    public record IncidentRef(Long id, String title, String status, String severity, Integer riskScore) {
    }

    public record AssetDetail(AssetView asset, List<IncidentRef> incidents) {
    }

    public record ImportResult(int created, int updated, int skipped, List<String> errors, int relinkedEvents) {
    }
}
