package com.sentinelai.incident.correlation;

import com.sentinelai.incident.domain.IncidentTimeline;
import com.sentinelai.incident.repository.IncidentTimelineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Appends entries to an incident's timeline. */
@Service
@RequiredArgsConstructor
public class TimelineService {

    public static final String INCIDENT_CREATED = "INCIDENT_CREATED";
    public static final String ALERT_JOINED = "ALERT_JOINED";
    public static final String RESCORED = "RESCORED";
    public static final String STATUS_CHANGE = "STATUS_CHANGE";
    public static final String ASSIGNMENT = "ASSIGNMENT";
    public static final String FEEDBACK = "FEEDBACK";
    public static final String ESCALATED = "ESCALATED";

    private final IncidentTimelineRepository timelineRepository;

    public void record(Long incidentId, String type, String actor, String detailJson) {
        timelineRepository.save(IncidentTimeline.builder()
                .incidentId(incidentId)
                .type(type)
                .actor(actor)
                .detail(detailJson)
                .build());
    }
}
