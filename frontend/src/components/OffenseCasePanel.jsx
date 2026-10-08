import { useEffect, useState } from 'react';
import { addNote, deleteNote, editNote, listAssignees } from '../services/offenses.service.js';
import { assignIncident, setPriority } from '../services/incidents.service.js';
import { PRIORITIES } from '../services/caseLabels.js';
import { useAuth } from '../context/AuthContext.jsx';
import { messageFromError } from '../services/errors.js';

/** Assignment, priority and analyst notes for a case. Viewers see everything read-only. */
export default function OffenseCasePanel({ offense, priority, notes, canEdit, onChanged }) {
  const { user, hasRole } = useAuth();
  const [editing, setEditing] = useState(null); // { id, body }
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

  async function changePriority(value) {
    try { await setPriority(offense.id, value); onChanged(); } catch (e) { setError(messageFromError(e)); }
  }

  async function saveEdit(e) {
    e.preventDefault();
    try { await editNote(offense.id, editing.id, editing.body); setEditing(null); onChanged(); }
    catch (err) { setError(messageFromError(err)); }
  }

  async function remove(n) {
    if (!window.confirm('Delete this note? The deletion stays in the audit log.')) return;
    try { await deleteNote(offense.id, n.id); onChanged(); } catch (err) { setError(messageFromError(err)); }
  }

  const mayChange = (n) => canEdit && (n.authorId === user?.id || hasRole('ADMIN'));

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
        <label>Priority{' '}
          {canEdit ? (
            <select value={priority || 'P3'} onChange={(e) => changePriority(e.target.value)} aria-label="Priority">
              {PRIORITIES.map((p) => <option key={p}>{p}</option>)}
            </select>
          ) : <strong>{priority}</strong>}
        </label>
        <span className="chip">Offense source: <code>{offense.offenseSource}</code></span>
        {offense.logSources.length > 0 && <span className="chip">Log sources: {offense.logSources.join(', ')}</span>}
      </div>

      <h4>Notes ({notes.length})</h4>
      {notes.length === 0 && <p className="muted small">No notes yet.</p>}
      <ul className="breakdown">
        {notes.map((n) => (
          <li key={n.id}>
            {editing?.id === n.id ? (
              <form onSubmit={saveEdit} style={{ flex: 1 }}>
                <textarea className="config-edit" rows={2} maxLength={2000} value={editing.body}
                  onChange={(e) => setEditing({ ...editing, body: e.target.value })} aria-label="Edit note" />
                <div className="filters">
                  <button type="submit" disabled={!editing.body.trim()}>Save</button>
                  <button type="button" className="ghost" onClick={() => setEditing(null)}>Cancel</button>
                </div>
              </form>
            ) : (
              <span style={{ whiteSpace: 'pre-wrap' }}>{n.body}</span>
            )}
            <span className="muted small">
              {n.author} · {new Date(n.createdAt).toLocaleString()}
              {n.updatedAt && <> · edited{n.editedBy ? ` by ${n.editedBy}` : ''}</>}
              {mayChange(n) && editing?.id !== n.id && (
                <>
                  {' '}<button className="ghost small" onClick={() => setEditing({ id: n.id, body: n.body })}>Edit</button>
                  <button className="ghost small" onClick={() => remove(n)}>Delete</button>
                </>
              )}
            </span>
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
