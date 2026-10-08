import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api.js';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../components/ui/index.js';
import { emitDataChanged } from '../hooks/useLiveRefresh.js';
import { messageFromError } from '../services/errors.js';

const KINDS = [
  { value: 'USERNAME', label: 'Fake username', hint: 'e.g. svc_backup — tripped by any login attempt or event naming it' },
  { value: 'API_KEY', label: 'Fake API key', hint: 'leave blank to generate — tripped when presented to the API' },
  { value: 'URL_PATH', label: 'Fake admin URL path', hint: 'e.g. /api/admin/backup-export — requests get a 404 and raise an incident' },
];

/** Honeytokens: plant decoys; any touch raises a CRITICAL "decoy touched" incident. */
export default function HoneytokensPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const { push } = useToast();
  const [decoys, setDecoys] = useState([]);
  const [form, setForm] = useState({ kind: 'USERNAME', value: '', description: '' });
  const [secret, setSecret] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try { setDecoys((await api.get('/honeytokens')).data.data); setError(null); } catch (e) { setError(messageFromError(e)); }
  }, []);
  useEffect(() => { load(); }, [load]);

  async function create(e) {
    e.preventDefault();
    try {
      const r = (await api.post('/honeytokens', { ...form, value: form.value || null })).data.data;
      setSecret(r.secret ? { value: r.secret, label: r.decoy.displayValue } : null);
      setForm({ ...form, value: '', description: '' });
      load();
    } catch (err) { setError(messageFromError(err)); }
  }

  async function test(d) {
    const value = d.kind === 'API_KEY' || d.kind === 'OTHER' ? window.prompt('Enter the decoy value (only its hash is stored)') : null;
    if ((d.kind === 'API_KEY' || d.kind === 'OTHER') && !value) return;
    try {
      await api.post(`/honeytokens/${d.id}/test`, value ? { value } : {});
      push('Decoy hit simulated — a CRITICAL incident was raised', { variant: 'success' });
      emitDataChanged('honeytoken');
      load();
    } catch (e) { setError(messageFromError(e)); }
  }

  return (
    <>
      <h2>Honeytokens</h2>
      <p className="subtitle">Decoys nobody legitimate ever uses. Any login, ingested event or request that touches one raises a
        CRITICAL, high-credibility incident explaining which decoy was touched. Values are stored only as hashes.</p>
      {error && <p className="error-text">{error}</p>}
      <section className="panel">
        <table className="data-table">
          <thead><tr><th>Kind</th><th>Decoy</th><th>Description</th><th>Hits</th><th>Last hit</th>{isAdmin && <th></th>}</tr></thead>
          <tbody>
            {decoys.map((d) => (
              <tr key={d.id}>
                <td>{d.kind}</td><td><code>{d.displayValue}</code></td><td className="small">{d.description || '—'}</td>
                <td className={d.triggeredCount > 0 ? 'error-text' : ''}>{d.triggeredCount}</td>
                <td className="small">{d.lastTriggeredAt ? new Date(d.lastTriggeredAt).toLocaleString() : '—'}</td>
                {isAdmin && <td>
                  <button className="ghost" onClick={() => test(d)}>Test</button>
                  <button className="ghost" onClick={async () => { try { await api.delete(`/honeytokens/${d.id}`); load(); } catch (e) { setError(messageFromError(e)); } }}>Delete</button>
                </td>}
              </tr>
            ))}
          </tbody>
        </table>
        <p className="muted small">Hits appear on the <Link to="/offenses">Offenses</Link> page and in the incident's case timeline as “Decoy touched”.</p>
      </section>
      {isAdmin && (
        <form className="panel" onSubmit={create}>
          <h3>Plant a decoy</h3>
          <div className="filters">
            <select value={form.kind} onChange={(e) => setForm({ ...form, kind: e.target.value })} aria-label="Decoy kind">
              {KINDS.map((k) => <option key={k.value} value={k.value}>{k.label}</option>)}
            </select>
            <input placeholder={form.kind === 'API_KEY' ? '(blank = generate)' : 'Decoy value'} maxLength={200}
              required={form.kind !== 'API_KEY'} value={form.value} onChange={(e) => setForm({ ...form, value: e.target.value })} />
            <input placeholder="Where it is planted (description)" maxLength={255} value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })} />
            <button type="submit">Create decoy</button>
          </div>
          <p className="muted small">{KINDS.find((k) => k.value === form.kind)?.hint}</p>
          {secret && (
            <div className="panel">
              <p className="error-text">Copy the decoy API key now — it is shown once and stored only as a hash:</p>
              <pre className="code-block">{secret.value}</pre>
              <button type="button" className="ghost" onClick={() => setSecret(null)}>Hide</button>
            </div>
          )}
        </form>
      )}
    </>
  );
}
