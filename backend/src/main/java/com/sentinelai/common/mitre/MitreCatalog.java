package com.sentinelai.common.mitre;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

/**
 * Offline MITRE ATT&CK lookup (technique id → name + tactic) loaded from the bundled
 * {@code mitre-techniques.json}. Unknown ids resolve to a generic entry, never an error, so a new
 * rule with an uncatalogued technique still renders.
 */
@Slf4j
@Component
public class MitreCatalog {

    public record Technique(String id, String name, String tactic) {
    }

    private record Entry(String name, String tactic) {
    }

    private final Map<String, Entry> techniques;

    public MitreCatalog(ObjectMapper objectMapper) {
        Map<String, Entry> loaded;
        try (InputStream in = new ClassPathResource("mitre-techniques.json").getInputStream()) {
            loaded = objectMapper.readValue(in, new TypeReference<Map<String, Entry>>() {});
        } catch (Exception e) {
            log.warn("Could not load bundled MITRE catalog; technique names will be generic: {}", e.toString());
            loaded = Map.of();
        }
        this.techniques = Map.copyOf(loaded);
    }

    /** Every catalogued technique (for coverage matrices), sorted by tactic then id. */
    public java.util.List<Technique> all() {
        return techniques.entrySet().stream().map(e -> new Technique(e.getKey(), e.getValue().name(), e.getValue().tactic()))
                .sorted(java.util.Comparator.comparing(Technique::tactic).thenComparing(Technique::id)).toList();
    }

    public Technique lookup(String id) {
        Entry e = id == null ? null : techniques.get(id);
        return e == null
                ? new Technique(id, "Technique " + id, "Unknown tactic")
                : new Technique(id, e.name(), e.tactic());
    }
}
