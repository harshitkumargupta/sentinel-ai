package com.sentinelai.honeytoken;

import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.ConflictException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.common.util.Hashing;
import com.sentinelai.honeytoken.domain.Honeytoken;
import com.sentinelai.honeytoken.domain.HoneytokenKind;
import com.sentinelai.honeytoken.repository.HoneytokenRepository;
import com.sentinelai.ingestion.IngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Decoys (honeytokens): create (value hashed; API keys generated and shown once), list, delete,
 * simulate a hit, and the tripwires that turn a real touch — a decoy API key on a request, or a
 * request to a decoy URL path — into an event through the normal pipeline (the HONEYTOKEN rule then
 * raises a CRITICAL incident). Decoy usernames trip via the login self-monitoring event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoneytokenService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._@\\-]{3,100}$");
    private static final Pattern PATH = Pattern.compile("^/[A-Za-z0-9._~/\\-]{1,200}$");

    private final HoneytokenRepository repository;
    private final OrganizationRepository organizations;
    private final IngestionService ingestion;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    /** Hashes of decoy URL paths, for the per-request path tripwire (refreshed on change). */
    private final Set<String> pathHashes = ConcurrentHashMap.newKeySet();
    private volatile boolean pathsLoaded;

    public record View(Long id, HoneytokenKind kind, String type, String displayValue, String description,
                       int triggeredCount, Instant lastTriggeredAt, Instant createdAt) {
    }

    /** {@code secret} is the raw value, returned only on create for generated API keys. */
    public record Created(View decoy, String secret) {
    }

    @Transactional(readOnly = true)
    public List<View> list(Long orgId) {
        return repository.findByOrg_Id(orgId).stream().map(HoneytokenService::view).toList();
    }

    @Transactional
    public Created create(AppUserPrincipal actor, HoneytokenKind kind, String value, String description) {
        String raw = value == null ? "" : value.trim();
        String secret = null;
        switch (kind) {
            case USERNAME -> require(USERNAME.matcher(raw).matches(), "Decoy usernames: 3–100 letters, digits or . _ @ -");
            case URL_PATH -> require(PATH.matcher(raw).matches(), "Decoy paths start with / and use URL-safe characters");
            case API_KEY -> {
                if (raw.isEmpty()) {
                    byte[] b = new byte[24];
                    random.nextBytes(b);
                    raw = "sk_" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);
                }
                require(raw.length() >= 16 && raw.length() <= 200, "Decoy API keys must be 16–200 characters");
                secret = raw;
            }
            case OTHER -> require(raw.length() >= 6 && raw.length() <= 200, "Decoy values must be 6–200 characters");
        }
        String hash = Hashing.sha256Hex(raw);
        if (repository.findFirstByOrg_IdAndValueHash(actor.getOrgId(), hash).isPresent()) {
            throw new ConflictException("That decoy already exists");
        }
        String display = kind == HoneytokenKind.API_KEY || kind == HoneytokenKind.OTHER ? mask(raw) : raw;
        Honeytoken t = repository.save(Honeytoken.builder().org(organizations.getReferenceById(actor.getOrgId()))
                .type(kind.name()).kind(kind).valueHash(hash).displayValue(display)
                .description(description == null || description.isBlank() ? null : description.trim())
                .triggeredCount(0).build());
        pathsLoaded = false;
        audit.record(actor.getOrgId(), actor.getUserId(), "HONEYTOKEN_CREATE", "honeytoken", t.getId(), "{\"kind\":\"" + kind + "\"}", null);
        return new Created(view(t), secret);
    }

    @Transactional
    public void delete(AppUserPrincipal actor, Long id) {
        Honeytoken t = load(actor.getOrgId(), id);
        repository.delete(t);
        pathsLoaded = false;
        audit.record(actor.getOrgId(), actor.getUserId(), "HONEYTOKEN_DELETE", "honeytoken", id, "{}", null);
    }

    /** Simulate a hit: ingest an event carrying the decoy's hash-matching value through the pipeline. */
    public Long test(AppUserPrincipal actor, Long id, String rawValue) {
        Honeytoken t = load(actor.getOrgId(), id);
        if (rawValue == null || !Hashing.sha256Hex(rawValue.trim()).equals(t.getValueHash())) {
            if (t.getKind() == HoneytokenKind.API_KEY || t.getKind() == HoneytokenKind.OTHER) {
                throw new BadRequestException("Enter the decoy's value to simulate a hit (only its hash is stored)");
            }
            rawValue = t.getDisplayValue();
        }
        return ingest(actor.getOrgId(), t.getKind(), rawValue.trim(), "203.0.113.66", "SentinelAI decoy test");
    }

    /** Tripwire: a request presented this API key and it is a decoy (in any org). */
    public void tripApiKey(String rawKey, String ip, String userAgent) {
        repository.findByValueHash(Hashing.sha256Hex(rawKey)).filter(t -> t.getKind() == HoneytokenKind.API_KEY)
                .ifPresent(t -> safeIngest(t.getOrg().getId(), HoneytokenKind.API_KEY, rawKey, ip, userAgent));
    }

    /** Tripwire: is this request path a decoy? If so, record the hit and return true. */
    public boolean tripPath(String path, String ip, String userAgent) {
        if (!pathsLoaded) {
            pathHashes.clear();
            repository.findByKind(HoneytokenKind.URL_PATH).forEach(t -> pathHashes.add(t.getValueHash()));
            pathsLoaded = true;
        }
        String hash = Hashing.sha256Hex(path);
        if (!pathHashes.contains(hash)) {
            return false;
        }
        repository.findByValueHash(hash).ifPresent(t -> safeIngest(t.getOrg().getId(), HoneytokenKind.URL_PATH, path, ip, userAgent));
        return true;
    }

    private void safeIngest(Long orgId, HoneytokenKind kind, String value, String ip, String ua) {
        try {
            ingest(orgId, kind, value, ip, ua);
        } catch (RuntimeException e) {
            log.warn("Could not record honeytoken hit: {}", e.toString()); // never break the request itself
        }
    }

    private Long ingest(Long orgId, HoneytokenKind kind, String value, String ip, String ua) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("eventType", "HONEYTOKEN_ACCESS");
        p.put("severity", "HIGH");
        p.put("honeytoken", true);
        p.put("sourceIp", ip);
        p.put("userAgent", ua);
        switch (kind) {
            case USERNAME -> p.put("username", value);
            case URL_PATH -> p.put("resource", value);
            default -> p.put("value", value);
        }
        return ingestion.ingest(orgId, null, "generic", objectMapper.valueToTree(p), null).eventId();
    }

    private Honeytoken load(Long orgId, Long id) {
        return repository.findById(id).filter(t -> t.getOrg().getId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Decoy not found: " + id));
    }

    private static View view(Honeytoken t) {
        return new View(t.getId(), t.getKind(), t.getType(), t.getDisplayValue() == null ? "(hash only)" : t.getDisplayValue(),
                t.getDescription(), t.getTriggeredCount(), t.getLastTriggeredAt(), t.getCreatedAt());
    }

    private static String mask(String v) {
        return v.length() <= 8 ? "****" : v.substring(0, 4) + "…" + v.substring(v.length() - 4);
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new BadRequestException(message);
        }
    }
}
