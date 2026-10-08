import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import DataState from '../components/DataState.jsx';
import SeverityBadge from '../components/SeverityBadge.jsx';
import MitreChip from '../components/MitreChip.jsx';
import RiskWaterfall from '../components/RiskWaterfall.jsx';
import StorylineGraph from '../components/StorylineGraph.jsx';
import Storyline3DLazy from '../components/three/Storyline3DLazy.jsx';
import AiInvestigationPanel from '../components/AiInvestigationPanel.jsx';
import ActionsPanel from '../components/ActionsPanel.jsx';
import AskAiPanel from '../components/AskAiPanel.jsx';
import SimilarIncidentsPanel from '../components/SimilarIncidentsPanel.jsx';
import MagnitudePanel from '../components/MagnitudePanel.jsx';
import OffenseCasePanel from '../components/OffenseCasePanel.jsx';
import { getOffense } from '../services/offenses.service.js';
import CaseTimeline from '../components/CaseTimeline.jsx';
import AffectedAssetsPanel from '../components/AffectedAssetsPanel.jsx';
import { NEXT_STATUS, statusLabel } from '../services/caseLabels.js';
import { useAuth } from '../context/AuthContext.jsx';
import {
  getIncident, getRisk, getTimeline, getEvidence, getGraph, updateStatus, setFeedback,
} from '../services/incidents.service.js';
import { messageFromError } from '../services/errors.js';


export default function IncidentDetailPage() {
  const { id } = useParams();
  const { hasRole } = useAuth();
  const [data, setData] = useState({ incident: null, risk: null, timeline: [], evidence: null, graph: null, offense: null });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [highlighted, setHighlighted] = useState([]);
  const [view3d, setView3d] = useState(false); // 2D storyline is the default; 3D is opt-in

  const load = useCallback(async () => {
    setError(null);
    try {
      const [detail, risk, timeline, evidence, graph, offense] = await Promise.all([
        getIncident(id), getRisk(id), getTimeline(id), getEvidence(id), getGraph(id), getOffense(id),
      ]);
      setData({ incident: detail.incident, risk, timeline, evidence, graph, offense });
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
    <>
        <DataState loading={loading} error={error} empty={!inc} emptyText="Incident not found.">
          {inc && (
            <>
              <div className="brand-row" style={{ justifyContent: 'space-between' }}>
                <h2>Offense #{inc.id}: {inc.title}</h2>
                <SeverityBadge severity={inc.severity} />
              </div>
              <p className="subtitle">Status: <strong>{statusLabel(inc.status)}</strong> · Priority: <strong>{inc.priority}</strong>
                {' '}· Assigned: <strong>{inc.assignedTo || 'unassigned'}</strong> · Feedback: {inc.feedback}</p>

              {canTriage && (
                <div className="filters">
                  {(NEXT_STATUS[inc.status] || []).map((s) => (
                    <button key={s} className="ghost" onClick={() => changeStatus(s)}>→ {statusLabel(s)}</button>
                  ))}
                  <button className="ghost" onClick={() => giveFeedback('TRUE_POSITIVE')}>Mark true positive</button>
                  <button className="ghost" onClick={() => giveFeedback('FALSE_POSITIVE')}>Mark false positive</button>
                </div>
              )}

              <MagnitudePanel magnitude={data.offense?.offense.magnitude} />

              <AffectedAssetsPanel assets={data.offense?.assets} />

              {data.offense?.threatIntel?.length > 0 && (
                <section className="panel">
                  <h3>Threat intelligence matches</h3>
                  <ul className="breakdown">
                    {data.offense.threatIntel.map((m) => (
                      <li key={`${m.ip}-${m.list}`}><code>{m.ip}</code><span className="chip">{m.list}</span></li>
                    ))}
                  </ul>
                </section>
              )}

              {data.offense && (
                <OffenseCasePanel offense={data.offense.offense} priority={inc.priority} notes={data.offense.notes}
                  canEdit={canTriage} onChanged={load} />
              )}

              {canTriage && (
                <AiInvestigationPanel incidentId={id} canReview={canTriage} onHighlight={setHighlighted} />
              )}

              <AskAiPanel incidentId={id} onHighlight={setHighlighted} />

              {canTriage && <ActionsPanel incidentId={id} canAct={canTriage} />}

              <SimilarIncidentsPanel incidentId={id} />

              <section className="panel">
                <h3>Risk breakdown</h3>
                <RiskWaterfall risk={data.risk} />
              </section>

              <section className="panel">
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <h3>Attack storyline</h3>
                  {data.graph && (data.graph.nodes?.length ?? 0) > 0 && (
                    <button className="ui-btn ui-btn--sm" onClick={() => setView3d((v) => !v)}
                      aria-pressed={view3d}>{view3d ? '2D view' : '3D view'}</button>
                  )}
                </div>
                {data.graph && (data.graph.nodes?.length ?? 0) > 0
                  ? (view3d
                      ? <Storyline3DLazy graph={data.graph} />
                      : <StorylineGraph graph={data.graph} />)
                  : <p className="muted">No graph data.</p>}
              </section>

              <div className="panel-grid">
                <CaseTimeline incidentId={id} refreshKey={data.timeline.length} />

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
                      <tr key={e.id} className={highlighted.includes(e.id) ? 'row-highlight' : ''}>
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
    </>
  );
}
