import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import NavBar from '../components/NavBar.jsx';
import DataState from '../components/DataState.jsx';
import SeverityBadge from '../components/SeverityBadge.jsx';
import MitreChip from '../components/MitreChip.jsx';
import RiskWaterfall from '../components/RiskWaterfall.jsx';
import StorylineGraph from '../components/StorylineGraph.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import {
  getIncident, getRisk, getTimeline, getEvidence, getGraph, updateStatus, setFeedback,
} from '../services/incidents.service.js';
import { messageFromError } from '../services/errors.js';

const NEXT_STATUS = {
  OPEN: ['INVESTIGATING', 'FALSE_POSITIVE'],
  INVESTIGATING: ['CONTAINED', 'FALSE_POSITIVE'],
  CONTAINED: ['RESOLVED', 'FALSE_POSITIVE'],
  RESOLVED: [],
  FALSE_POSITIVE: [],
};

export default function IncidentDetailPage() {
  const { id } = useParams();
  const { hasRole } = useAuth();
  const [data, setData] = useState({ incident: null, risk: null, timeline: [], evidence: null, graph: null });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [detail, risk, timeline, evidence, graph] = await Promise.all([
        getIncident(id), getRisk(id), getTimeline(id), getEvidence(id), getGraph(id),
      ]);
      setData({ incident: detail.incident, risk, timeline, evidence, graph });
    } catch (e) {
      setError(messageFromError(e));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { load(); }, [load]);

  async function changeStatus(status) {
    try { await updateStatus(id, status); load(); } catch (e) { setError(messageFromError(e)); }
  }
  async function giveFeedback(feedback) {
    try { await setFeedback(id, feedback); load(); } catch (e) { setError(messageFromError(e)); }
  }

  const inc = data.incident;
  const canTriage = hasRole('ANALYST', 'ADMIN');

  return (
    <div className="app-shell">
      <NavBar />
      <main className="content">
        <DataState loading={loading} error={error} empty={!inc} emptyText="Incident not found.">
          {inc && (
            <>
              <div className="brand-row" style={{ justifyContent: 'space-between' }}>
                <h2>Incident #{inc.id}: {inc.title}</h2>
                <SeverityBadge severity={inc.severity} />
              </div>
              <p className="subtitle">Status: <strong>{inc.status}</strong> · Feedback: {inc.feedback}</p>

              {canTriage && (
                <div className="filters">
                  {(NEXT_STATUS[inc.status] || []).map((s) => (
                    <button key={s} className="ghost" onClick={() => changeStatus(s)}>→ {s}</button>
                  ))}
                  <button className="ghost" onClick={() => giveFeedback('TRUE_POSITIVE')}>Mark true positive</button>
                  <button className="ghost" onClick={() => giveFeedback('FALSE_POSITIVE')}>Mark false positive</button>
                </div>
              )}

              <section className="panel">
                <h3>Risk breakdown</h3>
                <RiskWaterfall risk={data.risk} />
              </section>

              <section className="panel">
                <h3>Attack storyline</h3>
                {data.graph && (data.graph.nodes?.length ?? 0) > 0
                  ? <StorylineGraph graph={data.graph} />
                  : <p className="muted">No graph data.</p>}
              </section>

              <div className="panel-grid">
                <section className="panel">
                  <h3>Timeline</h3>
                  <ul className="breakdown">
                    {data.timeline.map((t) => (
                      <li key={t.id}>
                        <span>{t.type} <span className="muted small">{t.actor || 'system'}</span></span>
                        <span className="muted small">{new Date(t.createdAt).toLocaleTimeString()}</span>
                      </li>
                    ))}
                  </ul>
                </section>

                <section className="panel">
                  <h3>Alerts</h3>
                  <table className="data-table">
                    <thead><tr><th>Rule</th><th>Sev</th><th>MITRE</th></tr></thead>
                    <tbody>
                      {(data.evidence?.alerts ?? []).map((a) => (
                        <tr key={a.id}>
                          <td>{a.ruleType}</td>
                          <td><SeverityBadge severity={a.severity} /></td>
                          <td><MitreChip technique={a.mitreTechnique} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </section>
              </div>

              <section className="panel">
                <h3>Evidence events ({data.evidence?.events?.length ?? 0})</h3>
                <table className="data-table">
                  <thead><tr><th>ID</th><th>Type</th><th>User</th><th>IP</th><th>When</th></tr></thead>
                  <tbody>
                    {(data.evidence?.events ?? []).map((e) => (
                      <tr key={e.id}>
                        <td>{e.id}</td><td>{e.eventType}</td><td>{e.username || '—'}</td>
                        <td>{e.sourceIp || '—'}</td><td>{new Date(e.eventTimestamp).toLocaleString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>
            </>
          )}
        </DataState>
      </main>
    </div>
  );
}
