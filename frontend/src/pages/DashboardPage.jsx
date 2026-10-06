import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Bar, BarChart, Cell, Pie, PieChart, ResponsiveContainer, Tooltip as RTooltip, XAxis, YAxis,
} from 'recharts';
import NavBar from '../components/NavBar.jsx';
import TuningCard from '../components/TuningCard.jsx';
import ThreatCoreLazy from '../components/three/ThreatCoreLazy.jsx';
import AttackGlobeLazy from '../components/three/AttackGlobeLazy.jsx';
import { Card, StatTile, Table, Badge, EmptyState, ErrorState, SkeletonLines } from '../components/ui/index.js';
import { getSummary, getAlertReduction, getMitreCoverage, getGeoFlows } from '../services/dashboard.service.js';
import { listEvents } from '../services/events.service.js';
import { getPipelineStatus } from '../services/pipeline.service.js';
import { messageFromError } from '../services/errors.js';

const SEV_VAR = { LOW: 'var(--sev-low)', MEDIUM: 'var(--sev-medium)', HIGH: 'var(--sev-high)', CRITICAL: 'var(--sev-critical)' };
const CHART = ['var(--chart-1)', 'var(--chart-2)', 'var(--chart-3)', 'var(--chart-4)', 'var(--chart-5)', 'var(--chart-6)'];
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
  const seen = useRef(new Set());

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
    <div className="app-shell">
      <NavBar />
      <main className="content">
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

        {loading && !data.summary ? (
          <Card><SkeletonLines lines={4} /></Card>
        ) : (
          <>
            <div className="kpi-row">
              <Card><StatTile label="Open incidents" value={Number(data.summary?.incidentsByStatus?.OPEN || 0)} /></Card>
              <Card><StatTile label="Events / min" value={Number(eventsPerMin.toFixed(1))} /></Card>
              <Card><StatTile label="Alert reduction" value={data.reduction?.reductionPct || 0} suffix="%" /></Card>
              <Card><StatTile label="Events (24h)" value={Number(data.summary?.eventsLast24h || 0)} /></Card>
              <Card><StatTile label="Critical+High" value={Number((data.summary?.incidentsBySeverity?.CRITICAL || 0) + (data.summary?.incidentsBySeverity?.HIGH || 0))} /></Card>
            </div>

            <div className="dash-grid">
              <Card title="Attack origins" subtitle="Live geo flows to protected sites" className="dash-grid__globe">
                <AttackGlobeLazy flows={data.geo} height={320} />
              </Card>

              <Card title="Events by severity">
                <div style={{ height: 200 }}>
                  <ResponsiveContainer>
                    <PieChart>
                      <Pie data={sevData} dataKey="value" nameKey="name" innerRadius={45} outerRadius={75} paddingAngle={2}>
                        {sevData.map((d) => <Cell key={d.name} fill={SEV_VAR[d.name] || 'var(--chart-1)'} />)}
                      </Pie>
                      <RTooltip contentStyle={{ background: 'var(--bg-elev)', border: '1px solid var(--border)' }} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              </Card>

              <Card title="Top event types">
                <div style={{ height: 200 }}>
                  <ResponsiveContainer>
                    <BarChart data={typeData} layout="vertical" margin={{ left: 10 }}>
                      <XAxis type="number" hide />
                      <YAxis type="category" dataKey="name" width={130} tick={{ fill: 'var(--muted)', fontSize: 11 }} />
                      <RTooltip contentStyle={{ background: 'var(--bg-elev)', border: '1px solid var(--border)' }} />
                      <Bar dataKey="value" radius={[0, 4, 4, 0]}>
                        {typeData.map((d, i) => <Cell key={d.name} fill={CHART[i % CHART.length]} />)}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </Card>

              <Card title="MITRE ATT&CK coverage" className="dash-grid__mitre">
                {data.mitre.length === 0 ? <EmptyState title="No techniques yet" /> : (
                  <div className="mitre-heat" role="img" aria-label="MITRE technique heatmap">
                    {data.mitre.map((m) => {
                      const t = m.alertCount / maxMitre;
                      return (
                        <div key={m.technique} className="mitre-cell"
                          title={`${m.technique}: ${m.alertCount} alerts, ${m.ruleCount} rules`}
                          style={{ background: m.alertCount === 0 ? 'var(--panel-2)' : `color-mix(in srgb, var(--danger) ${20 + t * 70}%, transparent)` }}>
                          <span>{m.technique}</span>
                          <strong>{m.alertCount}</strong>
                        </div>
                      );
                    })}
                  </div>
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
              <Table columns={eventColumns} rows={events} rowKey={(r) => r.id} newRowKeys={newIds}
                maxHeight={320} emptyLabel="No recent events" />
            </Card>

            <div style={{ marginTop: 'var(--sp-4)' }}><TuningCard /></div>
          </>
        )}
      </main>
    </div>
  );
}
