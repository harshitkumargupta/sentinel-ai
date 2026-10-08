package com.sentinelai.threatintel;

import com.sentinelai.detection.buildingblock.Cidr;
import com.sentinelai.offense.ThreatIntelSignal;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads offline IP blocklists at startup (bundled files + an optional local directory) and answers
 * "is this IP listed?" — feeding offense credibility ({@link ThreatIntelSignal}) and the
 * {@code TI:<list>} pseudo reference sets usable in building blocks. Invalid lines are counted and
 * skipped, never fatal; a broken file never stops startup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreatIntelService implements ThreatIntelSignal {

    public record Feed(String id, String name, String origin, int ips, int cidrs, int invalidLines) {
    }

    public record Lookup(String ip, List<Match> matches) {
    }

    private record Loaded(Feed feed, Set<String> ips, List<String> cidrs) {
    }

    private final ThreatIntelProperties properties;
    private final Clock clock;

    private volatile List<Loaded> lists = List.of();
    private volatile Instant loadedAt;

    @PostConstruct
    public synchronized void reload() {
        if (!properties.isEnabled()) {
            lists = List.of();
            loadedAt = clock.instant();
            return;
        }
        List<Loaded> out = new ArrayList<>();
        try {
            for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:threatintel/*.txt")) {
                try (InputStream in = r.getInputStream()) {
                    out.add(parse(id(r.getFilename()), "bundled", new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                }
            }
        } catch (IOException e) {
            log.warn("Could not read bundled threat-intel lists: {}", e.toString());
        }
        String dir = properties.getDirectory();
        if (dir != null && !dir.isBlank()) {
            try (Stream<Path> files = Files.list(Path.of(dir))) {
                for (Path p : files.filter(f -> f.toString().endsWith(".txt")).sorted().toList()) {
                    out.add(parse(id(p.getFileName().toString()), "local", Files.readString(p)));
                }
            } catch (IOException e) {
                log.warn("Could not read threat-intel directory {}: {}", dir, e.toString());
            }
        }
        lists = List.copyOf(out);
        loadedAt = clock.instant();
        log.info("Loaded {} threat-intel list(s) ({} entries)", out.size(),
                out.stream().mapToInt(l -> l.ips().size() + l.cidrs().size()).sum());
    }

    public List<Feed> feeds() {
        return lists.stream().map(Loaded::feed).toList();
    }

    public Instant loadedAt() {
        return loadedAt;
    }

    @Override
    public List<Match> matches(Collection<String> ips) {
        List<Match> out = new ArrayList<>();
        for (String ip : ips) {
            for (Loaded l : lists) {
                if (listed(l, ip)) {
                    out.add(new Match(ip, l.feed().name()));
                }
            }
        }
        return out;
    }

    public Lookup check(String ip) {
        return new Lookup(ip, matches(List.of(ip)));
    }

    /** {@code TI:<list id or name>} membership, for building blocks. */
    public boolean contains(String listRef, String ip) {
        return lists.stream()
                .filter(l -> l.feed().id().equalsIgnoreCase(listRef) || l.feed().name().equalsIgnoreCase(listRef))
                .anyMatch(l -> listed(l, ip));
    }

    private static boolean listed(Loaded l, String ip) {
        return ip != null && (l.ips().contains(ip) || l.cidrs().stream().anyMatch(c -> Cidr.contains(c, ip)));
    }

    static Loaded parse(String id, String origin, String text) {
        Set<String> ips = new HashSet<>();
        List<String> cidrs = new ArrayList<>();
        String name = id;
        int invalid = 0;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.startsWith("# name:")) {
                name = line.substring("# name:".length()).strip();
                continue;
            }
            int hash = line.indexOf('#');
            if (hash >= 0) {
                line = line.substring(0, hash).strip();
            }
            if (line.isEmpty()) {
                continue;
            }
            if (line.contains("/") && Cidr.isValid(line)) {
                cidrs.add(line);
            } else if (!line.contains("/") && Cidr.isIpv4(line)) {
                ips.add(line);
            } else {
                invalid++;
            }
        }
        return new Loaded(new Feed(id, name, origin, ips.size(), cidrs.size(), invalid), Set.copyOf(ips), List.copyOf(cidrs));
    }

    private static String id(String filename) {
        String f = filename == null ? "list" : filename;
        return f.endsWith(".txt") ? f.substring(0, f.length() - 4) : f;
    }
}
