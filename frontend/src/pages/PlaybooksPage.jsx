import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../components/ui/index.js';
import { listPlaybooks, savePlaybook, deletePlaybook, listRuns } from '../services/soar.service.js';
import { messageFromError } from '../services/errors.js';

const EXAMPLE = JSON.stringify([
  { type: 'CREATE_CASE', priority: 'P2' }, { type: 'NOTIFY' }, { type: 'PROPOSE_BLOCK_IP' },
  { type: 'ADD_TO_WATCHLIST', set: 'Watchlist IPs' },
], null, 2);
const EMPTY = { name: '', description: '', triggerRuleType: '', triggerMinSeverity: 'HIGH', steps: EXAMPLE, autoRun: false };
const STATUS = { SUCCEEDED: 'badge-valid', PARTIAL: 'badge-fallback', FAILED: 'badge-rejected' };

/** SOAR playbooks: trigger + ordered steps (JSON), enable/auto-run, and the run history. */
export default function PlaybooksPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const { push } = useToast();
  const [playbooks, setPlaybooks] = useState([]);
  const [runs, setRuns] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [editing, setEditing] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try { const [p, r] = await Promise.all([listPlaybooks(), listRuns()]); setPlaybooks(p); setRuns(r); setError(null); }
    catch (e) { setError(messageFromError(e)); }
  }, []);
  useEffect(() => { load(); }, [load]);
  const pbName = (id) => playbooks.find((p) => p.id === id)?.name || `#${id}`;

  async function submit(e) {
    e.preventDefault();
    let steps;
    try { steps = JSON.parse(form.steps); } catch { setError('Steps must be valid JSON'); return; }
    try {
      await savePlaybook(editing, { ...form, steps, triggerRuleType: form.triggerRuleType || null, triggerMinSeverity: form.triggerMinSeverity || null });
      push('Playbook saved', { variant: 'success' }); setForm(EMPTY); setEditing(null); load();
    } catch (err) { setError(messageFromError(err)); }
  }

  const toggle = async (p, patch) => {
    try { await savePlaybook(p.id, { ...p, ...patch }); load(); } catch (e) { setError(messageFromError(e)); }
  };

  return (
    <>
      <h2>Playbooks</h2>
      <p className="subtitle">Automated response recipes. Steps that change systems only <em>propose</em> actions — they still
        go through Dry-run → Approve → Execute → Rollback, and every adapter is simulated.</p>
      {error && <p className="error-text">{error}</p>}
      <section className="panel">
        <table className="data-table">
          <thead><tr><th>Playbook</th><th>Trigger</th><th>Steps</th><th>Enabled</th><th>Auto-run</th>{isAdmin && <th></th>}</tr></thead>
          <tbody>
            {playbooks.map((p) => (
              <tr key={p.id}>
                <td><strong>{p.name}</strong><div className="muted small">{p.description}</div></td>
                <td className="small">{p.triggerRuleType || 'any rule'}{p.triggerMinSeverity && <> · ≥ {p.triggerMinSeverity}</>}</td>
                <td className="small"><ol>{p.steps.map((s, i) => <li key={i}>{s.type}{s.priority ? ` ${s.priority}` : ''}{s.set ? ` → ${s.set}` : ''}</li>)}</ol></td>
                <td>{isAdmin ? <button className="ghost" onClick={() => toggle(p, { enabled: !p.enabled })}>{p.enabled ? 'On' : 'Off'}</button> : (p.enabled ? 'On' : 'Off')}</td>
                <td>{isAdmin ? <button className="ghost" onClick={() => toggle(p, { autoRun: !p.autoRun })}>{p.autoRun ? 'On' : 'Off'}</button> : (p.autoRun ? 'On' : 'Off')}</td>
                {isAdmin && <td>
                  <button className="ghost" onClick={() => { setEditing(p.id); setForm({ ...p, triggerRuleType: p.triggerRuleType || '', triggerMinSeverity: p.triggerMinSeverity || '', steps: JSON.stringify(p.steps, null, 2) }); }}>Edit</button>
                  <button className="ghost" onClick={async () => { try { await deletePlaybook(p.id); load(); } catch (e) { setError(messageFromError(e)); } }}>Delete</button>
                </td>}
              </tr>
            ))}
          </tbody>
        </table>
        <p className="muted small">Run a playbook from an offense page (it lists the playbooks whose trigger matches).</p>
      </section>

      {isAdmin && (
        <form className="panel" onSubmit={submit}>
          <h3>{editing ? 'Edit playbook' : 'New playbook'}</h3>
          <div className="filters">
            <input placeholder="Name" required maxLength={100} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            <input placeholder="Trigger rule type (e.g. BRUTE_FORCE)" maxLength={50} value={form.triggerRuleType} onChange={(e) => setForm({ ...form, triggerRuleType: e.target.value })} />
            <select value={form.triggerMinSeverity} onChange={(e) => setForm({ ...form, triggerMinSeverity: e.target.value })} aria-label="Minimum severity">
              <option value="">Any severity</option>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((s) => <option key={s}>{s}</option>)}
            </select>
            <label><input type="checkbox" checked={form.autoRun} onChange={(e) => setForm({ ...form, autoRun: e.target.checked })} /> auto-run on new incidents</label>
          </div>
          <input style={{ width: '100%' }} placeholder="Description" maxLength={500} value={form.description || ''} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          <label className="muted small">Steps (JSON). Types: CREATE_CASE {'{priority}'}, NOTIFY {'{channelId?}'}, PROPOSE_BLOCK_IP, PROPOSE_DISABLE_USER, ADD_TO_WATCHLIST {'{set}'}
            <textarea className="config-edit" rows={8} value={form.steps} onChange={(e) => setForm({ ...form, steps: e.target.value })} />
          </label>
          <div className="filters">
            <button type="submit">Save playbook</button>
            {editing && <button type="button" className="ghost" onClick={() => { setEditing(null); setForm(EMPTY); }}>Cancel</button>}
          </div>
        </form>
      )}

      <section className="panel">
        <h3>Run history</h3>
        {runs.length === 0 ? <p className="muted small">No runs yet.</p> : (
          <table className="data-table">
            <thead><tr><th>When</th><th>Playbook</th><th>Incident</th><th>By</th><th>Status</th><th>Steps</th></tr></thead>
            <tbody>
              {runs.map((r) => (
                <tr key={r.id}>
                  <td className="small">{new Date(r.createdAt).toLocaleString()}</td><td>{pbName(r.playbookId)}</td>
                  <td>{r.incidentId ? <Link to={`/offenses/${r.incidentId}`}>#{r.incidentId}</Link> : '—'}</td><td>{r.triggeredBy}</td>
                  <td><span className={`ai-badge ${STATUS[r.status]}`}>{r.status}</span></td>
                  <td className="small">{r.steps.map((s) => `${s.ok ? '✓' : '✗'} ${s.type}`).join(' · ')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}
