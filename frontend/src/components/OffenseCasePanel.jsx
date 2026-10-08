import { useEffect, useState } from 'react';
import { addNote, listAssignees } from '../services/offenses.service.js';
import { assignIncident } from '../services/incidents.service.js';
import { messageFromError } from '../services/errors.js';

/** Assignment and analyst notes for an offense. Viewers see both read-only. */
export default function OffenseCasePanel({ offense, notes, canEdit, onChanged }) {
  const [assignees, setAssignees] = useState([]);
  const [body, setBody] = useState('');
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (canEdit) listAssignees().then(setAssignees).catch(() => setAssignees([]));
  }, [canEdit]);

  async function assign(value) {
    try { await assignIncident(offense.id, value ? Number(value) : null); onChanged(); }
    catch (e) { setError(messageFromError(e)); }
  }

  async function submit(e) {
    e.preventDefault();
    if (!body.trim()) return;
    setSaving(true);
    try { await addNote(offense.id, body); setBody(''); onChanged(); }
    catch (err) { setError(messageFromError(err)); }
    finally { setSaving(false); }
  }

  return (
    <section className="panel">
      <h3>Case</h3>
      {error && <p className="error-text">{error}</p>}
      <div className="filters">
        <label>Assigned to{' '}
          {canEdit ? (
            <select value={offense.assignedToId ?? ''} onChange={(e) => assign(e.target.value)} aria-label="Assignee">
              <option value="">Unassigned</option>
              {assignees.map((a) => <option key={a.id} value={a.id}>{a.username} ({a.role.toLowerCase()})</option>)}
            </select>
          ) : <strong>{offense.assignedTo || 'Unassigned'}</strong>}
        </label>
        <span className="chip">Offense source: <code>{offense.offenseSource}</code></span>
        {offense.logSources.length > 0 && <span className="chip">Log sources: {offense.logSources.join(', ')}</span>}
      </div>

      <h4>Notes ({notes.length})</h4>
      {notes.length === 0 && <p className="muted small">No notes yet.</p>}
      <ul className="breakdown">
        {notes.map((n) => (
          <li key={n.id}>
            <span style={{ whiteSpace: 'pre-wrap' }}>{n.body}</span>
            <span className="muted small">{n.author} · {new Date(n.createdAt).toLocaleString()}</span>
          </li>
        ))}
      </ul>
      {canEdit && (
        <form onSubmit={submit}>
          <textarea className="config-edit" rows={3} maxLength={2000} placeholder="Add an analyst note…"
            value={body} onChange={(e) => setBody(e.target.value)} aria-label="New note" />
          <div className="filters">
            <button type="submit" disabled={saving || !body.trim()}>{saving ? 'Saving…' : 'Add note'}</button>
            <span className="muted small">{body.length}/2000</span>
          </div>
        </form>
      )}
    </section>
  );
}
