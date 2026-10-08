import { useCallback, useEffect, useState } from 'react';
import { getCaseTimeline } from '../services/incidents.service.js';
import { useLiveRefresh } from '../hooks/useLiveRefresh.js';
import { messageFromError } from '../services/errors.js';

const KINDS = [
  { id: 'STATUS', label: 'Status' }, { id: 'ASSIGNMENT', label: 'Assignment' }, { id: 'NOTE', label: 'Notes' },
  { id: 'ACTION', label: 'Response' }, { id: 'AI', label: 'AI' }, { id: 'DETECTION', label: 'Detection' },
  { id: 'SYSTEM', label: 'System' },
];
const ICON = { STATUS: '◆', ASSIGNMENT: '☺', NOTE: '✎', ACTION: '⚡', AI: '✦', DETECTION: '⚑', SYSTEM: '·' };

/** Merged case timeline (status, assignment, notes, response actions, AI, detection) with kind filters. */
export default function CaseTimeline({ incidentId, refreshKey }) {
  const [entries, setEntries] = useState(null);
  const [error, setError] = useState(null);
  const [hidden, setHidden] = useState(() => new Set(['SYSTEM']));

  const load = useCallback(async () => {
    try { setEntries(await getCaseTimeline(incidentId)); setError(null); } catch (e) { setError(messageFromError(e)); }
  }, [incidentId]);
  useEffect(() => { load(); }, [load, refreshKey]);
  useLiveRefresh(load, 0);

  function toggle(kind) {
    setHidden((h) => { const n = new Set(h); if (n.has(kind)) n.delete(kind); else n.add(kind); return n; });
  }

  const shown = (entries ?? []).filter((e) => !hidden.has(e.kind));
  return (
    <section className="panel">
      <h3>Case timeline</h3>
      <div className="chip-row">
        {KINDS.map((k) => (
          <button key={k.id} className={`evidence-chip ${hidden.has(k.id) ? 'muted' : ''}`} aria-pressed={!hidden.has(k.id)}
            onClick={() => toggle(k.id)}>{ICON[k.id]} {k.label}</button>
        ))}
      </div>
      {error && <p className="error-text">{error}</p>}
      {entries && shown.length === 0 && <p className="muted small">Nothing to show for the selected kinds.</p>}
      <ul className="breakdown">
        {shown.map((e, i) => (
          <li key={i}>
            <span>
              <span aria-hidden="true">{ICON[e.kind]} </span><strong>{e.title}</strong>
              {e.detail && <div className="muted small" style={{ whiteSpace: 'pre-wrap' }}>{e.detail}</div>}
            </span>
            <span className="muted small">{e.actor} · {new Date(e.at).toLocaleString()}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
