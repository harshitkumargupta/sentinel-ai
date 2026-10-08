import { useCallback, useState } from 'react';
import DataState from '../components/DataState.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useLiveRefresh } from '../hooks/useLiveRefresh.js';
import {
  SOURCE_TYPES, listLogSources, createLogSource, setLogSourceEnabled, rotateLogSourceKey, deleteLogSource,
} from '../services/logsources.service.js';
import { messageFromError } from '../services/errors.js';

const HEALTH_STYLE = {
  RECEIVING: { background: '#1a7f37', label: 'receiving' },
  IDLE: { background: '#9a6700', label: 'idle' },
  NEVER: { background: '#57606a', label: 'no events yet' },
  DISABLED: { background: '#8250df', label: 'disabled' },
};

function ingestSnippet(key) {
  const origin = typeof window !== 'undefined' ? window.location.origin : 'http://localhost';
  return `curl -X POST ${origin}/api/ingest/events \\
  -H 'X-API-Key: ${key}' -H 'Content-Type: application/json' \\
  -d '{"eventType":"FAILED_LOGIN","severity":"LOW","username":"jdoe","sourceIp":"203.0.113.5"}'`;
}

/** QRadar-style Log Sources: onboard a source (type + one-time key), watch health/EPS, manage keys. */
export default function LogSourcesPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const [sources, setSources] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [form, setForm] = useState({ name: '', type: 'WEB_SERVER', description: '' });
  const [newKey, setNewKey] = useState(null); // { name, apiKey } — shown once

  const load = useCallback(async () => {
    try { setSources(await listLogSources()); setError(null); } catch (e) { setError(messageFromError(e)); }
    finally { setLoading(false); }
  }, []);
  useLiveRefresh(load, 5000);

  async function act(fn) {
    try { await fn(); await load(); } catch (e) { setError(messageFromError(e)); }
  }

  async function handleCreate(e) {
    e.preventDefault();
    await act(async () => {
      const res = await createLogSource(form);
      setNewKey({ name: res.source.name, apiKey: res.apiKey.apiKey });
      setForm({ name: '', type: form.type, description: '' });
    });
  }

  async function handleDelete(s) {
    if (!window.confirm(`Delete log source "${s.name}"? Its keys stop working; past events are kept.`)) return;
    await act(() => deleteLogSource(s.id));
  }

  return (
    <>
      <h2>Log Sources</h2>
      <p className="subtitle">Systems that send events to SentinelAI. Each source has its own ingest key.</p>
      {error && <p className="error-text">{error}</p>}

      {isAdmin && (
        <section className="panel">
          <h3>Add a log source</h3>
          <form className="filters" onSubmit={handleCreate}>
            <input placeholder="Name (e.g. web-01 nginx)" value={form.name} maxLength={150} required
              onChange={(e) => setForm({ ...form, name: e.target.value })} />
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })} aria-label="Source type">
              {SOURCE_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
            </select>
            <input placeholder="Description (optional)" value={form.description} maxLength={500}
              onChange={(e) => setForm({ ...form, description: e.target.value })} />
            <button type="submit">Create + issue key</button>
          </form>
          {newKey && (
            <div className="panel" style={{ marginTop: '1rem' }}>
              <p className="error-text">Copy the key for “{newKey.name}” now — it is shown only once (only a hash is stored):</p>
              <pre className="code-block">{newKey.apiKey}</pre>
              <h4>Send a test event</h4>
              <pre className="code-block">{ingestSnippet(newKey.apiKey)}</pre>
              <button className="ghost" onClick={() => setNewKey(null)}>I saved it — hide</button>
            </div>
          )}
        </section>
      )}

      <DataState loading={loading} error={!sources && error} empty={(sources ?? []).length === 0} emptyText="No log sources yet.">
        <table className="data-table">
          <thead>
            <tr><th>Name</th><th>Type</th><th>Status</th><th>Last event</th><th>EPS</th><th>Total events</th>
              <th>Parse errors</th><th>Keys</th>{isAdmin && <th></th>}</tr>
          </thead>
          <tbody>
            {(sources ?? []).map((s) => {
              const h = HEALTH_STYLE[s.health] || HEALTH_STYLE.NEVER;
              return (
                <tr key={s.id}>
                  <td><strong>{s.name}</strong>{s.description && <div className="muted small">{s.description}</div>}</td>
                  <td>{s.type}</td>
                  <td><span className="status-badge" style={{ background: h.background }}>{h.label}</span></td>
                  <td>{s.lastEventAt ? new Date(s.lastEventAt).toLocaleString() : '—'}</td>
                  <td>{s.eventsPerSecond.toFixed(2)}</td>
                  <td>{s.totalEvents.toLocaleString()}</td>
                  <td className={s.parseErrors > 0 ? 'error-text' : ''}>{s.parseErrors}</td>
                  <td>{s.activeKeys}</td>
                  {isAdmin && (
                    <td>
                      <button className="ghost" onClick={() => act(() => setLogSourceEnabled(s.id, !s.enabled))}>
                        {s.enabled ? 'Disable' : 'Enable'}
                      </button>
                      <button className="ghost" onClick={() => act(async () => {
                        const k = await rotateLogSourceKey(s.id);
                        setNewKey({ name: s.name, apiKey: k.apiKey });
                      })}>Rotate key</button>
                      {s.id !== 1 && <button className="ghost" onClick={() => handleDelete(s)}>Delete</button>}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </DataState>
    </>
  );
}
