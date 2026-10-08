import { Suspense, lazy, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLiveRefresh } from '../hooks/useLiveRefresh.js';
import { useNavigate } from 'react-router-dom';
import TuningCard from '../components/TuningCard.jsx';
import PinnedSearchWidgets from '../components/PinnedSearchWidgets.jsx';
import ThreatCoreLazy from '../components/three/ThreatCoreLazy.jsx';
import AttackGlobeLazy from '../components/three/AttackGlobeLazy.jsx';
import { Card, StatTile, Table, Badge, EmptyState, ErrorState, SkeletonLines } from '../components/ui/index.js';
import { getSummary, getAlertReduction, getMitreCoverage, getGeoFlows } from '../services/dashboard.service.js';
import { listEvents } from '../services/events.service.js';
import { getPipelineStatus } from '../services/pipeline.service.js';
import { messageFromError } from '../services/errors.js';

const DashboardCharts = lazy(() => import('../components/DashboardCharts.jsx'));
const REFRESH_MS = 15000;

function threatLevel(summary) {
  const sev = summary?.incidentsBySeverity || {};
  if ((sev.CRITICAL || 0) > 0) return 'CRITICAL';
  if ((sev.HIGH || 0) > 0) return 'HIGH';
  if ((sev.MEDIUM || 0) > 0) return 'MEDIUM';
  return 'LOW';
}

export default function DashboardPage() {
  const [data, setData] = useState({ summary: null, reduction: null, mitre: [], geo: [], pipeline: null });
  const [events, setEvents] = useState([]);
  const [newIds, setNewIds] = useState(new Set());
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [paused, setPaused] = useState(false);
  const [updatedAt, setUpdatedAt] = useState(null);
  const [countryFilter, setCountryFilter] = useState(null);
  const seen = useRef(new Set());
  const navigate = useNavigate();

  // Clicking a source on the globe filters the live feed to that origin and lets the analyst jump
  // straight to the related incidents.
  const selectCountry = useCallback((cc) => {
    setCountryFilter(cc);
    setPaused(true); // freeze the feed so the filtered view doesn't shift under the analyst.
  }, []);

  const load = useCallback(async () => {
    try {
      const [summary, reduction, mitre, geo] = await Promise.all([
        getSummary(), getAlertReduction(), getMitreCoverage(), getGeoFlows(),
      ]);
      let pipeline = null;
      try { pipeline = await getPipelineStatus(); } catch { pipeline = null; /* disabled → 404 */ }
      const page = await listEvents({ size: 20, sort: 'eventTimestamp,desc' });
      const rows = page?.content || [];
      const fresh = new Set();
      rows.forEach((e) => { if (!seen.current.has(e.id)) fresh.add(e.id); seen.current.add(e.id); });
      setData({ summary, reduction, mitre, geo, pipeline });
      setEvents(rows);
      setNewIds(fresh);
      setError(null);
      setUpdatedAt(new Date());
    } catch (e) {
      setError(messageFromError(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);
  // Reload immediately when a scenario/upload/action announces new data (unless paused).
  useLiveRefresh(() => { if (!paused) load(); }, 0);
  useEffect(() => {
    if (paused) return undefined;
    const t = setInterval(load, REFRESH_MS);
    return () => clearInterval(t);
  }, [paused, load]);

  const level = threatLevel(data.summary);
  const sevData = useMemo(() => Object.entries(data.summary?.eventsBySeverity || {})
    .map(([name, value]) => ({ name, value: Number(value) })), [data.summary]);
  const typeData = useMemo(() => Object.entries(data.summary?.eventsByType || {})
    .map(([name, value]) => ({ name, value: Number(value) }))
    .filter((d) => d.value > 0).sort((a, b) => b.value - a.value).slice(0, 6), [data.summary]);
  const maxMitre = Math.max(1, ...data.mitre.map((m) => m.alertCount));
  const eventsPerMin = data.summary ? (data.summary.eventsLast24h / 1440) : 0;
  const shownEvents = useMemo(
    () => (countryFilter ? events.filter((e) => e.geoCountry === countryFilter) : events),
    [events, countryFilter],
  );

  const eventColumns = [
    { key: 'eventTimestamp', header: 'Time', sortable: true, width: 150,
      render: (r) => new Date(r.eventTimestamp).toLocaleTimeString() },
    { key: 'eventType', header: 'Type', sortable: true },
    { key: 'severity', header: 'Severity', sortable: true,
      render: (r) => <Badge variant={r.severity}>{r.severity}</Badge> },
    { key: 'sourceIp', header: 'Source IP', render: (r) => r.sourceIp || '—' },
    { key: 'username', header: 'User', render: (r) => r.username || '—' },
  ];

  return (
    <>
        <div className="dash-head">
          <div className="dash-head__title">
            <ThreatCoreLazy size={44} level={level} />
            <div>
              <h2 style={{ margin: 0 }}>Command Center</h2>
              <span className="ui-card__subtitle">
                Threat level <Badge variant={level}>{level}</Badge>
                {updatedAt && <> · updated {updatedAt.toLocaleTimeString()}</>}
              </span>
            </div>
          </div>
          <button className="ui-btn ui-btn--sm" onClick={() => setPaused((p) => !p)}
            aria-pressed={paused}>{paused ? '▶ Resume' : '⏸ Pause'} auto-refresh</button>
        </div>

        {error && <ErrorState message={error} onRetry={load} />}

        <PinnedSearchWidgets />

        {loading && !data.summary ? (
          <Card><SkeletonLines lines={4} /></Card>
        ) : (
          <>
            <div className="kpi-row stagger-in">
              <Card><StatTile label="Open incidents" value={Number(data.summary?.incidentsByStatus?.OPEN || 0)} /></Card>
              <Card><StatTile label="Events / min" value={Number(eventsPerMin.toFixed(1))} /></Card>
              <Card><StatTile label="Alert reduction" value={data.reduction?.reductionPct || 0} suffix="%" /></Card>
              <Card><StatTile label="Events (24h)" value={Number(data.summary?.eventsLast24h || 0)} /></Card>
              <Card><StatTile label="Critical+High" value={Number((data.summary?.incidentsBySeverity?.CRITICAL || 0) + (data.summary?.incidentsBySeverity?.HIGH || 0))} /></Card>
            </div>

            <div className="dash-grid stagger-in">
              <Card title="Attack origins" subtitle="Live geo flows to protected sites" className="dash-grid__globe">
                <AttackGlobeLazy flows={data.geo} height={320} onSelectCountry={selectCountry} />
              </Card>

              <Suspense fallback={<><Card title="Events by severity"><SkeletonLines lines={3} /></Card><Card title="Top event types"><SkeletonLines lines={3} /></Card></>}>
                <DashboardCharts sevData={sevData} typeData={typeData} />
              </Suspense>

              <Card title="MITRE ATT&CK coverage" subtitle={data.mitre.length ? `${data.mitre.length} technique${data.mitre.length === 1 ? '' : 's'}` : undefined}
                className="dash-grid__mitre">
                {data.mitre.length === 0 ? <EmptyState title="No techniques yet" message="Mapped techniques appear as detections fire." /> : (
                  <>
                    <div className="mitre-heat" role="img" aria-label="MITRE technique heatmap">
                      {data.mitre.map((m) => {
                        const t = m.alertCount / maxMitre;
                        return (
                          <div key={m.technique} className={`mitre-cell${m.alertCount === 0 ? ' mitre-cell--empty' : ''}`}
                            title={`${m.technique}: ${m.alertCount} alerts, ${m.ruleCount} rules`}
                            style={m.alertCount === 0 ? undefined : { background: `color-mix(in srgb, var(--danger) ${20 + t * 70}%, transparent)` }}>
                            <span>{m.technique}</span>
                            <strong>{m.alertCount}</strong>
                          </div>
                        );
                      })}
                    </div>
                    <div className="mitre-scale">
                      <span>fewer alerts</span>
                      <span className="mitre-scale__ramp" aria-hidden="true" />
                      <span>more</span>
                    </div>
                  </>
                )}
              </Card>

              <Card title="Pipeline health" subtitle="Kafka lag / DLQ">
                {data.pipeline ? (
                  <div className="pipe-health">
                    <StatTile label="Consumer lag" value={Number(data.pipeline.totalLag ?? data.pipeline.lag ?? 0)} />
                    <StatTile label="DLQ messages" value={Number(data.pipeline.dlqCount ?? 0)} />
                  </div>
                ) : <EmptyState icon="⇄" title="Pipeline disabled" message="Kafka is not enabled on this instance." />}
              </Card>

              <Card title="AI activity" subtitle="Investigations">
                <div className="pipe-health">
                  <StatTile label="Incidents reviewed" value={Number((data.summary?.incidentsByStatus?.RESOLVED || 0) + (data.summary?.incidentsByStatus?.CONTAINED || 0))} />
                  <StatTile label="Open" value={Number(data.summary?.incidentsByStatus?.OPEN || 0)} />
                </div>
              </Card>
            </div>

            <Card title="Live event stream" subtitle={paused ? 'paused' : 'auto-refreshing'}
              style={{ marginTop: 'var(--sp-4)' }}>
              {countryFilter && (
                <div className="feed-filter">
                  <span>Filtered to <strong>{countryFilter}</strong></span>
                  <button className="ui-btn ui-btn--sm" onClick={() => navigate('/incidents')}>Open incidents →</button>
                  <button className="ui-btn ui-btn--sm" onClick={() => { setCountryFilter(null); setPaused(false); }}>Clear ✕</button>
                </div>
              )}
              <Table columns={eventColumns} rows={shownEvents} rowKey={(r) => r.id} newRowKeys={newIds}
                maxHeight={320} emptyLabel={countryFilter ? `No recent events from ${countryFilter}` : 'No recent events'} />
            </Card>

            <div style={{ marginTop: 'var(--sp-4)' }}><TuningCard /></div>
          </>
        )}
    </>
  );
}
