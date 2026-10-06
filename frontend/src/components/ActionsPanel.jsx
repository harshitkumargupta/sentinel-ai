import { useCallback, useEffect, useState } from 'react';
import {
  listActions, dryRunAction, approveAction, rejectAction, executeAction, rollbackAction,
} from '../services/playbook.service.js';
import { messageFromError } from '../services/errors.js';

const STATUS_CLASS = {
  PROPOSED: 'badge-fallback', APPROVED: 'chip', EXECUTED: 'badge-valid',
  ROLLED_BACK: 'chip', REJECTED: 'badge-rejected', FAILED: 'badge-rejected', EXPIRED: 'chip',
};

/** SOAR actions panel: dry-run preview, approve/reject/execute/rollback, status badges + history. */
export default function ActionsPanel({ incidentId, canAct }) {
  const [actions, setActions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(null);

  const load = useCallback(async () => {
    try {
      setActions(await listActions(incidentId));
    } catch (e) {
      setError(messageFromError(e));
    } finally {
      setLoading(false);
    }
  }, [incidentId]);

  useEffect(() => { load(); }, [load]);

  async function run(id, fn) {
    setBusy(id);
    setError(null);
    try {
      await fn(id);
      await load();
    } catch (e) {
      setError(messageFromError(e));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="panel">
      <h3>Response actions</h3>
      {error && <p className="error-text">{error}</p>}
      {loading && <div className="state-box">Loading…</div>}
      {!loading && actions.length === 0 && (
        <p className="muted">No actions yet. Approve an AI analysis to propose response actions.</p>
      )}

      {actions.map((a) => (
        <div key={a.id} className="action-row">
          <div className="chip-row">
            <span className={`ai-badge ${STATUS_CLASS[a.status] || 'chip'}`}>{a.status}</span>
            <strong><code>{a.actionType}</code> {a.target}</strong>
            {a.riskLevel && <span className="chip">risk {a.riskLevel}</span>}
            {a.destructive && <span className="chip">destructive</span>}
          </div>
          {a.reason && <p className="muted small">{a.reason}</p>}

          {a.dryRun && (
            <p className={a.dryRun.allowed ? 'muted small' : 'error-text'}>
              Dry-run: {a.dryRun.allowed
                ? `${a.dryRun.summary} — ${a.dryRun.blastRadius}`
                : `refused — ${a.dryRun.reason}`}
            </p>
          )}
          {a.status === 'FAILED' && a.failureReason && (
            <p className="error-text small">Failed: {a.failureReason}</p>
          )}

          {canAct && (
            <div className="filters">
              {a.status === 'PROPOSED' && (
                <>
                  <button className="ghost" disabled={busy === a.id} onClick={() => run(a.id, dryRunAction)}>Dry-run</button>
                  <button disabled={busy === a.id} onClick={() => run(a.id, approveAction)}>Approve</button>
                  <button className="ghost" disabled={busy === a.id} onClick={() => run(a.id, rejectAction)}>Reject</button>
                </>
              )}
              {a.status === 'APPROVED' && (
                <>
                  <button className="ghost" disabled={busy === a.id} onClick={() => run(a.id, dryRunAction)}>Dry-run</button>
                  <button disabled={busy === a.id} onClick={() => run(a.id, executeAction)}>Execute</button>
                </>
              )}
              {a.status === 'EXECUTED' && (
                <button className="ghost" disabled={busy === a.id} onClick={() => run(a.id, rollbackAction)}>Rollback</button>
              )}
            </div>
          )}
        </div>
      ))}
    </section>
  );
}
