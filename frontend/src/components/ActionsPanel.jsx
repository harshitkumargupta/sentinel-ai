import { useCallback, useEffect, useState } from 'react';
import {
  listActions, dryRunAction, approveAction, rejectAction, executeAction, rollbackAction,
  listActionTargets, proposeAction,
} from '../services/playbook.service.js';
import { messageFromError } from '../services/errors.js';
import { emitDataChanged } from '../hooks/useLiveRefresh.js';

/** One-click proposals: which action applies to which kind of evidence target. */
const QUICK = [
  { type: 'block_ip', label: 'Block IP', from: 'ips' },
  { type: 'disable_user', label: 'Disable User', from: 'users' },
  { type: 'force_password_reset', label: 'Force Password Reset', from: 'users' },
  { type: 'isolate_host', label: 'Isolate Host', from: 'hosts' },
];

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
  const [targets, setTargets] = useState({ ips: [], users: [], hosts: [] });

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
  useEffect(() => {
    if (canAct) listActionTargets(incidentId).then(setTargets).catch(() => {});
  }, [incidentId, canAct]);

  async function run(id, fn) {
    setBusy(id);
    setError(null);
    try {
      await fn(id);
      await load();
      emitDataChanged('action');
    } catch (e) {
      setError(messageFromError(e));
    } finally {
      setBusy(null);
    }
  }

  /** Approve; if the admin-risk guard asks for step-up, confirm with the user and retry once. */
  async function approve(id) {
    try {
      await approveAction(id, false);
    } catch (e) {
      const msg = e?.response?.data?.error?.message || '';
      if (e?.response?.status === 403 && msg.includes('Admin-risk guard')
          && window.confirm(`${msg}\n\nConfirm this approval (step-up)?`)) {
        await approveAction(id, true);
      } else {
        throw e;
      }
    }
  }

  async function propose(type, target) {
    setBusy(`${type}:${target}`);
    setError(null);
    try {
      await proposeAction(incidentId, type, target);
      await load();
    } catch (e) {
      setError(messageFromError(e));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="panel">
      <h3>Response actions <span className="chip">simulated — no real systems are changed</span></h3>
      {error && <p className="error-text">{error}</p>}

      {canAct && (
        <div className="quick-actions">
          {QUICK.map((q) => (targets[q.from] || []).map((t) => (
            <button key={`${q.type}:${t}`} className="ghost" disabled={busy !== null}
              onClick={() => propose(q.type, t)}>
              {q.label}: <code>{t}</code>
            </button>
          )))}
          {targets.ips.length + targets.users.length + targets.hosts.length === 0 && (
            <p className="muted small">No IPs, users or hosts in this incident's evidence to act on.</p>
          )}
          <p className="muted small">Proposing adds the action below; then Dry-run → Approve → Execute (and Rollback).</p>
        </div>
      )}

      {loading && <div className="state-box">Loading…</div>}
      {!loading && actions.length === 0 && (
        <p className="muted">No actions yet. Propose one above, or approve an AI analysis.</p>
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
                  <button disabled={busy === a.id} onClick={() => run(a.id, approve)}>Approve</button>
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
