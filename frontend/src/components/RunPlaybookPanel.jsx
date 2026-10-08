import { useEffect, useState } from 'react';
import { matchingPlaybooks, runPlaybook } from '../services/soar.service.js';
import { messageFromError } from '../services/errors.js';
import { emitDataChanged } from '../hooks/useLiveRefresh.js';

const STATUS = { SUCCEEDED: 'badge-valid', PARTIAL: 'badge-fallback', FAILED: 'badge-rejected' };

/** Run a matching SOAR playbook on this incident; shows each step's outcome. */
export default function RunPlaybookPanel({ incidentId, canRun, onRan }) {
  const [playbooks, setPlaybooks] = useState([]);
  const [run, setRun] = useState(null);
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState(null);
  useEffect(() => { matchingPlaybooks(incidentId).then(setPlaybooks).catch(() => setPlaybooks([])); }, [incidentId]);
  if (playbooks.length === 0) return null;

  async function go(p) {
    setBusy(p.id); setError(null);
    try { setRun({ name: p.name, ...(await runPlaybook(p.id, incidentId)) }); emitDataChanged('playbook'); onRan?.(); }
    catch (e) { setError(messageFromError(e)); }
    finally { setBusy(null); }
  }

  return (
    <section className="panel">
      <h3>Playbooks <span className="chip">SOAR — proposes actions, never executes them</span></h3>
      <div className="filters">
        {playbooks.map((p) => (
          <button key={p.id} disabled={!canRun || busy !== null} title={p.description || ''} onClick={() => go(p)}>
            {busy === p.id ? 'Running…' : `Run: ${p.name}`}
          </button>
        ))}
      </div>
      {error && <p className="error-text">{error}</p>}
      {run && (
        <div className="small">
          <p><strong>{run.name}</strong> <span className={`ai-badge ${STATUS[run.status]}`}>{run.status}</span></p>
          <ol>{run.steps.map((s, i) => <li key={i} className={s.ok ? '' : 'error-text'}>{s.type}: {s.detail}</li>)}</ol>
          <p className="muted">Proposed actions appear under Response actions for dry-run and approval.</p>
        </div>
      )}
    </section>
  );
}
