package com.sentinelai.detection.sandbox;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.demo.SampleDatasets;
import com.sentinelai.detection.buildingblock.BuildingBlockMatcher;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.engine.AlertDraft;
import com.sentinelai.detection.engine.BacktestRuleContext;
import com.sentinelai.detection.engine.DetectionRuleEvaluator;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.detection.service.RuleConfigValidator;
import com.sentinelai.event.domain.EventOutcome;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.ingestion.normalize.EventNormalizer;
import com.sentinelai.ingestion.normalize.NormalizedEvent;
import com.sentinelai.ingestion.parse.LogParserService;
import com.sentinelai.ingestion.parse.ParsedRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What-if sandbox for a rule: replays stored events (a time range) or a bundled sample dataset
 * through the rule's evaluator twice — current config vs. an unsaved edited config — and diffs the
 * alerts. Pure dry run: nothing is persisted, no alerts/incidents are created, no counters move.
 */
@Service
public class RuleSandboxService {

    static final int MAX_EVENTS = 20_000;
    static final int MAX_SAMPLES = 10;

    private final DetectionRuleRepository rules;
    private final SecurityEventRepository events;
    private final OrganizationRepository organizations;
    private final Map<String, DetectionRuleEvaluator> evaluators;
    private final Map<String, EventNormalizer> normalizers;
    private final BuildingBlockMatcher buildingBlocks;
    private final RuleConfigValidator validator;
    private final LogParserService parser;
    private final Clock clock;

    public RuleSandboxService(DetectionRuleRepository rules, SecurityEventRepository events, OrganizationRepository organizations,
                              List<DetectionRuleEvaluator> evaluatorBeans, List<EventNormalizer> normalizerBeans,
                              BuildingBlockMatcher buildingBlocks, RuleConfigValidator validator, LogParserService parser, Clock clock) {
        this.rules = rules;
        this.events = events;
        this.organizations = organizations;
        this.evaluators = evaluatorBeans.stream().collect(Collectors.toMap(DetectionRuleEvaluator::type, Function.identity()));
        this.normalizers = normalizerBeans.stream().collect(Collectors.toMap(EventNormalizer::sourceType, Function.identity()));
        this.buildingBlocks = buildingBlocks;
        this.validator = validator;
        this.parser = parser;
        this.clock = clock;
    }

    public record Request(String editedConfig, Severity editedSeverity, Instant from, Instant to, String datasetId) {
    }

    public record EventSample(Long id, Instant time, String type, String sourceIp, String username, String resource) {
    }

    public record Fire(String message, String entityKey, Severity severity, List<EventSample> events) {
    }

    public record Side(String config, long alerts, Set<String> entities, List<Fire> samples) {
    }

    public record Result(String source, long eventsScanned, Side current, Side edited, List<String> newlyAlerting,
                         List<String> noLongerAlerting, String summary) {
    }

    @Transactional(readOnly = true)
    public Result run(Long ruleId, Request req, AppUserPrincipal actor) {
        DetectionRule rule = rules.findById(ruleId).filter(r -> r.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Rule not found: " + ruleId));
        DetectionRuleEvaluator evaluator = Optional.ofNullable(evaluators.get(rule.getRuleType()))
                .orElseThrow(() -> new BadRequestException("No evaluator for rule type " + rule.getRuleType()));
        String edited = validator.validate(actor.getOrgId(), rule.getRuleType(), req.editedConfig());

        String source;
        List<SecurityEvent> evs;
        if (req.datasetId() != null && !req.datasetId().isBlank()) {
            SampleDatasets.Dataset ds = SampleDatasets.find(req.datasetId())
                    .orElseThrow(() -> new NotFoundException("Unknown dataset: " + req.datasetId()));
            evs = datasetEvents(ds, organizations.getReferenceById(actor.getOrgId()));
            source = "sample dataset " + ds.label();
        } else {
            if (req.from() == null || req.to() == null || !req.from().isBefore(req.to())) {
                throw new BadRequestException("Choose a time range (from before to) or a sample dataset");
            }
            evs = events.findByOrg_IdAndEventTimestampBetweenOrderByEventTimestampAsc(actor.getOrgId(), req.from(), req.to());
            source = "stored events " + req.from() + " → " + req.to();
        }
        if (evs.size() > MAX_EVENTS) {
            throw new BadRequestException("Too many events (" + evs.size() + "); narrow the range (max " + MAX_EVENTS + ")");
        }
        Map<Long, SecurityEvent> byId = evs.stream().filter(e -> e.getId() != null)
                .collect(Collectors.toMap(SecurityEvent::getId, e -> e, (a, b) -> a));

        Side current = side(evaluator, probe(rule, rule.getConfig(), rule.getSeverity()), evs, byId);
        Side changed = side(evaluator, probe(rule, edited, req.editedSeverity() != null ? req.editedSeverity() : rule.getSeverity()), evs, byId);
        List<String> added = changed.entities().stream().filter(e -> !current.entities().contains(e)).toList();
        List<String> removed = current.entities().stream().filter(e -> !changed.entities().contains(e)).toList();
        String summary = "%d → %d alert(s) over %d event(s); %d newly alerting entit%s, %d no longer alerting."
                .formatted(current.alerts(), changed.alerts(), evs.size(), added.size(), added.size() == 1 ? "y" : "ies", removed.size());
        return new Result(source, evs.size(), current, changed, added, removed, summary);
    }

    private Side side(DetectionRuleEvaluator evaluator, DetectionRule probe, List<SecurityEvent> evs, Map<Long, SecurityEvent> byId) {
        BacktestRuleContext ctx = new BacktestRuleContext();
        long count = 0;
        Set<String> entities = new LinkedHashSet<>();
        List<Fire> samples = new ArrayList<>();
        for (SecurityEvent e : evs) {
            ctx.advance(e);
            if (!buildingBlocks.matches(e, probe)) {
                continue;
            }
            Optional<AlertDraft> d = evaluator.evaluate(e, probe, ctx);
            if (d.isEmpty()) {
                continue;
            }
            count++;
            entities.add(d.get().entityKey());
            if (samples.size() < MAX_SAMPLES) {
                List<EventSample> ev = d.get().matchedEventIds() == null ? List.of() : d.get().matchedEventIds().stream()
                        .map(byId::get).filter(java.util.Objects::nonNull).limit(5).map(RuleSandboxService::sample).toList();
                samples.add(new Fire(d.get().message(), d.get().entityKey(), d.get().severity(), ev.isEmpty() ? List.of(sample(e)) : ev));
            }
        }
        return new Side(probe.getConfig(), count, entities, samples);
    }

    private static DetectionRule probe(DetectionRule rule, String config, Severity severity) {
        return DetectionRule.builder().id(rule.getId()).org(rule.getOrg()).name(rule.getName()).ruleType(rule.getRuleType())
                .severity(severity).mitreTechnique(rule.getMitreTechnique()).version(rule.getVersion())
                .config(config).enabled(true).build();
    }

    /** Parse a bundled dataset into transient (never saved) events with synthetic ids, in time order. */
    private List<SecurityEvent> datasetEvents(SampleDatasets.Dataset ds, Organization org) {
        List<String> lines;
        try (InputStream in = new ClassPathResource(ds.resource()).getInputStream()) {
            lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (Exception e) {
            throw new IllegalStateException("Bundled dataset missing: " + ds.resource(), e);
        }
        List<SecurityEvent> out = new ArrayList<>();
        long id = 1;
        for (ParsedRecord rec : parser.parse(ds.format(), lines, clock.instant()).records()) {
            NormalizedEvent n = normalizers.getOrDefault(rec.sourceType(), normalizers.get("generic")).normalize(rec.payload());
            out.add(SecurityEvent.builder().id(id++).org(org).eventType(n.getEventType()).severity(n.getSeverity())
                    .outcome(n.getOutcome() != null ? n.getOutcome() : EventOutcome.defaultFor(n.getEventType()))
                    .sourceIp(n.getSourceIp()).username(n.getUsername()).userAgent(n.getUserAgent()).resource(n.getResource())
                    .geoCountry(n.getGeoCountry()).honeytoken(n.isHoneytoken()).rawPayload(n.getRawPayload())
                    .entityKey(n.getEntityKey() != null ? n.getEntityKey() : n.getUsername() != null ? "user:" + n.getUsername()
                            : n.getSourceIp() != null ? "ip:" + n.getSourceIp() : null)
                    .eventTimestamp(n.getEventTimestamp() != null ? n.getEventTimestamp() : clock.instant()).build());
        }
        out.sort(Comparator.comparing(SecurityEvent::getEventTimestamp));
        return out;
    }

    private static EventSample sample(SecurityEvent e) {
        return new EventSample(e.getId(), e.getEventTimestamp(), e.getEventType() == null ? null : e.getEventType().name(),
                e.getSourceIp(), e.getUsername(), e.getResource());
    }
}
