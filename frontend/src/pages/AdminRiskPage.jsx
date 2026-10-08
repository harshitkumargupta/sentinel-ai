import { useCallback, useEffect, useState } from 'react';
import DataState from '../components/DataState.jsx';
import RiskBandBadge from '../components/RiskBandBadge.jsx';
import {
  listPending, approvePending, rejectPending, adminTimeline, listSessions, revokeSessions, guardedDisableUser,
} from '../services/admin.service.js';
import { messageFromError } from '../services/errors.js';

export default function AdminRiskPage() {
  const [pending, setPending] = useState([]);
  const [timeline, setTimeline] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [decision, setDecision] = useState(null);
  const [disableId, setDisableId] = useState('');
  const [sessionUserId, setSessionUserId] = useState('');
  const [sessions, setSessions] = useState(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [p, t] = await Promise.all([listPending(), adminTimeline({ days: 30, size: 50 })]);
      setPending(p);
      setTimeline(t.content || []);
    } catch (e) {
      setError(messageFromError(e));
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { load(); }, [load]);

  async function act(fn) {
    try { await fn(); load(); } catch (e) { setError(messageFromError(e)); }
  }

  async function runGuardedDisable(stepUp) {
    try { setDecision(await guardedDisableUser(Number(disableId), stepUp)); load(); }
    catch (e) { setError(messageFromError(e)); }
  }

  return (
    <>
        <h2>Admin risk</h2>
        {error && <p className="error-text">{error}</p>}

        <section className="panel">
          <h3>Risky action (demo): disable a user</h3>
          <div className="filters">
            <input placeholder="user id" value={disableId} onChange={(e) => setDisableId(e.target.value)} />
            <button onClick={() => runGuardedDisable(false)}>Attempt disable</button>
            <button className="ghost" onClick={() => runGuardedDisable(true)}>With step-up</button>
          </div>
          {decision && (
            <p>
              <RiskBandBadge band={decision.band} /> <strong>{decision.decision}</strong> (risk {decision.score}) — {decision.explanation}
            </p>
          )}
        </section>

        <DataState loading={loading} error={error} empty={false}>
          <section className="panel">
            <h3>Pending approvals ({pending.length})</h3>
            {pending.length === 0 ? <p className="muted">Nothing awaiting approval.</p> : (
              <table className="data-table">
                <thead><tr><th>ID</th><th>Action</th><th>Entity</th><th>Risk</th><th>Requested by</th><th>Expires</th><th></th></tr></thead>
                <tbody>
                  {pending.map((p) => (
                    <tr key={p.id}>
                      <td>{p.id}</td><td>{p.action}</td><td>{p.entityType} #{p.entityId}</td><td>{p.riskScore}</td>
                      <td>{p.requestedBy}</td><td>{new Date(p.expiresAt).toLocaleTimeString()}</td>
                      <td>
                        <button className="ghost" onClick={() => act(() => approvePending(p.id))}>Approve</button>
                        <button className="ghost" onClick={() => act(() => rejectPending(p.id))}>Reject</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>

          <section className="panel">
            <h3>Sessions · force logout</h3>
            <div className="filters">
              <input placeholder="user id" value={sessionUserId} onChange={(e) => setSessionUserId(e.target.value)} />
              <button className="ghost" onClick={async () => { try { setSessions(await listSessions(Number(sessionUserId))); } catch (e) { setError(messageFromError(e)); } }}>List</button>
              <button onClick={() => act(async () => { await revokeSessions(Number(sessionUserId)); setSessions(await listSessions(Number(sessionUserId))); })}>Force logout</button>
            </div>
            {sessions && (
              <ul className="breakdown">
                {sessions.map((s) => (
                  <li key={s.id}><span>session #{s.id}</span><span className={s.revoked ? 'muted' : ''}>{s.revoked ? 'revoked' : 'active'}</span></li>
                ))}
              </ul>
            )}
          </section>

          <section className="panel">
            <h3>Admin activity timeline</h3>
            <table className="data-table">
              <thead><tr><th>Action</th><th>Actor</th><th>Entity</th><th>Details (before/after)</th><th>When</th></tr></thead>
              <tbody>
                {timeline.map((t) => (
                  <tr key={t.id}>
                    <td>{t.action}</td><td>{t.actorId ?? 'system'}</td><td>{t.entityType}{t.entityId ? ' #' + t.entityId : ''}</td>
                    <td className="small muted">{t.details}</td>
                    <td>{new Date(t.createdAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        </DataState>
    </>
  );
}
