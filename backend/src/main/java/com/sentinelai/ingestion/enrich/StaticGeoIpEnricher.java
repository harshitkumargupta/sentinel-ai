package com.sentinelai.ingestion.enrich;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Stub GeoIP enricher backed by a static IP-prefix → country map loaded from
 * {@code classpath:geoip-ranges.json}. Any load/lookup failure degrades to "unknown" (empty).
 */
@Slf4j
@Component
public class StaticGeoIpEnricher implements GeoIpEnricher {

    private final Map<String, String> prefixes = new LinkedHashMap<>();

    public StaticGeoIpEnricher(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("geoip-ranges.json").getInputStream()) {
            JsonNode node = objectMapper.readTree(in).get("prefixes");
            if (node != null) {
                node.fields().forEachRemaining(e -> prefixes.put(e.getKey(), e.getValue().asText()));
            }
            log.info("Loaded {} GeoIP prefix ranges", prefixes.size());
        } catch (Exception e) {
            log.warn("Could not load geoip-ranges.json; GeoIP enrichment disabled", e);
        }
    }

    @Override
    public Optional<String> country(String ip) {
        try {
            if (ip == null) {
                return Optional.empty();
            }
            return prefixes.entrySet().stream()
                    .filter(e -> ip.startsWith(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
