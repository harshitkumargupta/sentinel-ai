package com.sentinelai.offense;

import com.sentinelai.alert.domain.Alert;
import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.dto.EventResponse;
import com.sentinelai.incident.correlation.TimelineService;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentAlert;
import com.sentinelai.incident.domain.IncidentEvent;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.incident.repository.IncidentAlertRepository;
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.offense.web.OffenseDtos.Assignee;
import com.sentinelai.offense.web.OffenseDtos.NoteView;
import com.sentinelai.offense.web.OffenseDtos.OffenseDetail;
import com.sentinelai.offense.web.OffenseDtos.OffensePage;
import com.sentinelai.offense.web.OffenseDtos.OffenseSummary;
import com.sentinelai.site.domain.Site;
import com.sentinelai.site.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Offenses = SentinelAI incidents seen through QRadar's lens: each gets a magnitude (severity,
 * relevance, credibility — see {@link MagnitudeCalculator}) computed from its own alerts and events,
 * its offense source (the correlated entity), categories (rule types), log sources, assignee and
 * notes. Lists compute magnitude over the most recent {@code listScanLimit} incidents so they can be
 * sorted by it.
 */
@Service
@RequiredArgsConstructor
public class OffenseService {

    private static final int MAX_EVENTS_IN_DETAIL = 100;

    private final IncidentRepository incidentRepository;
    private final IncidentAlertRepository incidentAlertRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final IncidentNoteRepository noteRepository;
    private final SiteRepository siteRepository;
    private final UserRepository userRepository;
    private final MagnitudeCalculator calculator;
    private final MagnitudeProperties properties;
    private final ObjectProvider<ThreatIntelSignal> threatIntel;
    private final AuditService auditService;
    private final TimelineService timeline;
    private final Clock clock;

    public enum SortBy { MAGNITUDE, RECENT }

    /** Everything about one incident needed to summarize it. */
    private record Loaded(Incident incident, List<Alert> alerts, List<SecurityEvent> events,
                          List<ThreatIntelSignal.Match> intel) {
    }

    @Transactional(readOnly = true)
    public OffensePage list(AppUserPrincipal actor, IncidentStatus status, Long assigneeId, Integer minMagnitude,
                            SortBy sort, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        List<Incident> recent = incidentRepository.findAll(
                (root, q, cb) -> cb.equal(root.get("org").get("id"), actor.getOrgId()),
                PageRequest.of(0, properties.getListScanLimit(), Sort.by(Sort.Direction.DESC, "id"))).getContent();
        Map<Long, String> sourceNames = sourceNames(actor.getOrgId());
        List<OffenseSummary> all = recent.stream()
                .filter(i -> status == null || i.getStatus() == status)
                .filter(i -> assigneeId == null || (i.getAssignedTo() != null && assigneeId.equals(i.getAssignedTo().getId())))
                .map(i -> summarize(load(i), sourceNames))
                .filter(o -> minMagnitude == null || o.magnitude().magnitude() >= minMagnitude)
                .sorted(sort == SortBy.RECENT
                        ? Comparator.comparing(OffenseSummary::lastSeen, Comparator.nullsLast(Comparator.reverseOrder()))
                        : Comparator.comparing((OffenseSummary o) -> o.magnitude().magnitude()).reversed()
                                .thenComparing(OffenseSummary::id, Comparator.reverseOrder()))
                .toList();
        int from = Math.min(all.size(), Math.max(0, page) * safeSize);
        int to = Math.min(all.size(), from + safeSize);
        return new OffensePage(all.subList(from, to), page, safeSize, all.size(),
                (int) Math.ceil(all.size() / (double) safeSize));
    }

    @Transactional(readOnly = true)
    public OffenseDetail get(Long id, AppUserPrincipal actor) {
        Loaded l = load(incident(id, actor));
        List<EventResponse> events = l.events().stream()
                .sorted(Comparator.comparing(SecurityEvent::getEventTimestamp).reversed())
                .limit(MAX_EVENTS_IN_DETAIL).map(EventResponse::from).toList();
        return new OffenseDetail(summarize(l, sourceNames(actor.getOrgId())), l.intel(), events, notes(id, actor));
    }

    @Transactional(readOnly = true)
    public Magnitude magnitude(Long id, AppUserPrincipal actor) {
        return calculator.calculate(facts(load(incident(id, actor))));
    }

    @Transactional(readOnly = true)
    public List<NoteView> notes(Long id, AppUserPrincipal actor) {
        incident(id, actor);
        return noteRepository.findByIncident_IdOrderByIdAsc(id).stream().map(OffenseService::view).toList();
    }

    @Transactional
    public NoteView addNote(Long id, String body, AppUserPrincipal actor) {
        Incident incident = incident(id, actor);
        IncidentNote note = noteRepository.save(IncidentNote.builder()
                .incident(incident).author(userRepository.getReferenceById(actor.getUserId()))
                .body(clean(body)).build());
        auditService.record(actor.getOrgId(), actor.getUserId(), "OFFENSE_NOTE_ADD", "incident", id,
                "{\"noteId\":" + note.getId() + "}", null);
        timeline.record(id, "NOTE_ADDED", actor.getUsername(), "{\"noteId\":" + note.getId() + "}");
        return new NoteView(note.getId(), actor.getUsername(), actor.getUserId(), note.getBody(),
                note.getCreatedAt(), null, null);
    }

    /** Edit a note: its author or an admin. */
    @Transactional
    public NoteView editNote(Long id, Long noteId, String body, AppUserPrincipal actor) {
        IncidentNote note = ownNote(id, noteId, actor);
        note.setBody(clean(body));
        note.setUpdatedAt(Instant.now(clock));
        note.setEditedBy(userRepository.getReferenceById(actor.getUserId()));
        noteRepository.save(note);
        auditService.record(actor.getOrgId(), actor.getUserId(), "OFFENSE_NOTE_EDIT", "incident", id,
                "{\"noteId\":" + noteId + "}", null);
        timeline.record(id, "NOTE_EDITED", actor.getUsername(), "{\"noteId\":" + noteId + "}");
        return view(note);
    }

    /** Delete a note: its author or an admin. The audit log keeps the record of the deletion. */
    @Transactional
    public void deleteNote(Long id, Long noteId, AppUserPrincipal actor) {
        IncidentNote note = ownNote(id, noteId, actor);
        noteRepository.delete(note);
        auditService.record(actor.getOrgId(), actor.getUserId(), "OFFENSE_NOTE_DELETE", "incident", id,
                "{\"noteId\":" + noteId + "}", null);
        timeline.record(id, "NOTE_DELETED", actor.getUsername(), "{\"noteId\":" + noteId + "}");
    }

    private IncidentNote ownNote(Long id, Long noteId, AppUserPrincipal actor) {
        incident(id, actor);
        IncidentNote note = noteRepository.findById(noteId).filter(n -> n.getIncident().getId().equals(id))
                .orElseThrow(() -> new NotFoundException("Note not found: " + noteId));
        boolean author = note.getAuthor() != null && note.getAuthor().getId().equals(actor.getUserId());
        if (!author && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only the note's author or an admin can change it");
        }
        return note;
    }

    /** Users an offense can be assigned to: enabled analysts and admins of the org. */
    @Transactional(readOnly = true)
    public List<Assignee> assignees(AppUserPrincipal actor) {
        return userRepository.findByOrg_Id(actor.getOrgId()).stream()
                .filter(User::isEnabled)
                .filter(u -> u.getRole() == Role.ANALYST || u.getRole() == Role.ADMIN)
                .sorted(Comparator.comparing(User::getUsername))
                .map(u -> new Assignee(u.getId(), u.getUsername(), u.getRole().name())).toList();
    }

    // --- internals -----------------------------------------------------------------------------

    private Loaded load(Incident i) {
        List<Alert> alerts = incidentAlertRepository.findById_IncidentId(i.getId()).stream()
                .map(IncidentAlert::getAlert).toList();
        List<SecurityEvent> events = incidentEventRepository.findById_IncidentId(i.getId()).stream()
                .map(IncidentEvent::getEvent).toList();
        ThreatIntelSignal ti = threatIntel.getIfAvailable();
        List<ThreatIntelSignal.Match> intel = ti == null ? List.of()
                : ti.matches(events.stream().map(SecurityEvent::getSourceIp).filter(Objects::nonNull)
                        .collect(Collectors.toSet()));
        return new Loaded(i, alerts, events, intel);
    }

    private OffenseFacts facts(Loaded l) {
        Set<String> privileged = properties.getPrivilegedAccounts().stream()
                .map(String::toLowerCase).collect(Collectors.toSet());
        Set<String> users = l.events().stream().map(SecurityEvent::getUsername).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        boolean privilegedTarget = users.stream().anyMatch(u -> privileged.contains(u.toLowerCase()))
                || users.stream().anyMatch(u -> userRepository.findByUsername(u)
                        .map(x -> x.getRole() == Role.ADMIN).orElse(false));
        int maxCrit = l.events().stream().map(SecurityEvent::getAssetCriticality).filter(Objects::nonNull)
                .mapToInt(Byte::intValue).max().orElse(0);
        return new OffenseFacts(
                l.incident().getRiskScore() == null ? 0 : l.incident().getRiskScore(),
                privilegedTarget, maxCrit,
                (int) l.alerts().stream().map(Alert::getRuleType).distinct().count(),
                (int) l.events().stream().map(e -> e.getSite() == null ? null : e.getSite().getId())
                        .filter(Objects::nonNull).distinct().count(),
                (int) l.events().stream().map(SecurityEvent::getEventType).distinct().count(),
                l.intel().size(),
                l.incident().getFeedback());
    }

    private OffenseSummary summarize(Loaded l, Map<Long, String> sourceNames) {
        Incident i = l.incident();
        Instant first = l.events().stream().map(SecurityEvent::getEventTimestamp).min(Instant::compareTo).orElse(null);
        Instant last = l.events().stream().map(SecurityEvent::getEventTimestamp).max(Instant::compareTo).orElse(null);
        List<String> sources = l.events().stream().map(e -> e.getSite() == null ? null : e.getSite().getId())
                .filter(Objects::nonNull).distinct().map(id -> sourceNames.getOrDefault(id, "source #" + id))
                .sorted().toList();
        return new OffenseSummary(i.getId(), i.getTitle(),
                i.getStatus() == null ? null : i.getStatus().name(),
                i.getSeverity() == null ? null : i.getSeverity().name(),
                i.getRiskScore(), calculator.calculate(facts(l)), i.getCorrelationKey(),
                l.alerts().stream().map(Alert::getRuleType).distinct().sorted().toList(),
                l.events().size(), l.alerts().size(), sources,
                i.getAssignedTo() == null ? null : i.getAssignedTo().getUsername(),
                i.getAssignedTo() == null ? null : i.getAssignedTo().getId(),
                i.getFeedback() == null ? null : i.getFeedback().name(),
                noteRepository.countByIncident_Id(i.getId()), first, last, i.getCreatedAt());
    }

    private Map<Long, String> sourceNames(Long orgId) {
        return siteRepository.findByOrg_IdOrderByIdAsc(orgId).stream()
                .collect(Collectors.toMap(Site::getId, Site::getName, (a, b) -> a));
    }

    private Incident incident(Long id, AppUserPrincipal actor) {
        return incidentRepository.findById(id)
                .filter(i -> i.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Offense not found: " + id));
    }

    private static NoteView view(IncidentNote n) {
        return new NoteView(n.getId(), n.getAuthor() == null ? "deleted user" : n.getAuthor().getUsername(),
                n.getAuthor() == null ? null : n.getAuthor().getId(), n.getBody(), n.getCreatedAt(),
                n.getUpdatedAt(), n.getEditedBy() == null ? null : n.getEditedBy().getUsername());
    }

    /** Strip control characters (keeping newlines/tabs) and trim; length is capped by the DTO. */
    private static String clean(String body) {
        return body.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
    }
}
