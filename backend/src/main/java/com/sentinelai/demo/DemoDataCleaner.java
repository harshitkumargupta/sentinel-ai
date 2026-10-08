package com.sentinelai.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Removes demo data only: simulator events (labelled in {@code sim_labels}) and replayed sample events
 * (client id {@code replay:*}), alerts from simulator runs or replays ({@code run_id} =
 * a simulator run or {@code replay-*}), and incidents made <em>entirely</em> of such alerts (plus their
 * actions, analyses and notifications, which reference incidents with RESTRICT). Users, rules, log
 * sources, real ingested/uploaded events and the audit log are never touched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataCleaner {

    public record ResetResult(int incidents, int alerts, int events, int runs) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional
    public ResetResult reset(Long orgId) {
        MapSqlParameterSource p = new MapSqlParameterSource("org", orgId);

        List<Long> incidentIds = jdbc.queryForList("""
                select distinct ia.incident_id from incident_alerts ia
                join alerts a on a.id = ia.alert_id
                join incidents i on i.id = ia.incident_id
                where i.org_id = :org
                  and (a.run_id in (select run_id from simulator_runs) or a.run_id like 'replay-%')
                  and not exists (
                      select 1 from incident_alerts ia2 join alerts a2 on a2.id = ia2.alert_id
                      where ia2.incident_id = ia.incident_id
                        and (a2.run_id is null
                             or (a2.run_id not in (select run_id from simulator_runs) and a2.run_id not like 'replay-%')))
                """, p, Long.class);

        int incidents = 0;
        if (!incidentIds.isEmpty()) {
            MapSqlParameterSource ids = new MapSqlParameterSource("ids", incidentIds);
            jdbc.update("delete from notifications where incident_id in (:ids)", ids);
            jdbc.update("delete from playbook_actions where incident_id in (:ids)", ids);
            jdbc.update("delete from ai_analyses where incident_id in (:ids)", ids);
            incidents = jdbc.update("delete from incidents where id in (:ids)", ids); // cascades links + timeline
        }
        int alerts = jdbc.update("""
                delete from alerts where org_id = :org
                  and (run_id in (select run_id from simulator_runs) or run_id like 'replay-%')
                """, p);
        int events = jdbc.update("""
                delete from security_events where org_id = :org
                  and (id in (select event_id from (select event_id from sim_labels) l)
                       or client_event_id like 'replay:%')
                """, p); // sim_labels rows cascade
        int runs = jdbc.update("""
                delete from simulator_runs where run_id not in (select run_id from (select distinct run_id from sim_labels) l)
                """, p);
        log.info("Demo reset (org {}): {} incidents, {} alerts, {} events, {} runs removed",
                orgId, incidents, alerts, events, runs);
        return new ResetResult(incidents, alerts, events, runs);
    }
}
