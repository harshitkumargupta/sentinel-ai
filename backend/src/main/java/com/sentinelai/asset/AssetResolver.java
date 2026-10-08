package com.sentinelai.asset;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the inventoried asset an event concerns: the host in a {@code host:<name>} entity key, else
 * the source IP, else an IPv4 at the start of the resource (e.g. a firewall/port-scan target
 * {@code 10.20.0.15:22}). Hostnames match case-insensitively.
 */
@Component
@RequiredArgsConstructor
public class AssetResolver {

    private static final Pattern LEADING_IP = Pattern.compile("^(\\d{1,3}(?:\\.\\d{1,3}){3})(?:[:/].*)?$");

    private final AssetRepository repository;

    public Optional<Asset> resolve(Long orgId, String entityKey, String sourceIp, String resource) {
        if (entityKey != null && entityKey.startsWith("host:")) {
            Optional<Asset> byHost = repository.findByOrg_IdAndHostnameIgnoreCase(orgId, entityKey.substring(5));
            if (byHost.isPresent()) {
                return byHost;
            }
        }
        if (sourceIp != null) {
            Optional<Asset> byIp = repository.findByOrg_IdAndIp(orgId, sourceIp);
            if (byIp.isPresent()) {
                return byIp;
            }
        }
        if (resource != null) {
            Matcher m = LEADING_IP.matcher(resource.trim());
            if (m.matches()) {
                return repository.findByOrg_IdAndIp(orgId, m.group(1));
            }
        }
        return Optional.empty();
    }
}
