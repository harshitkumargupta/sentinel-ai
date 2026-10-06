import { useEffect, useState } from 'react';
import NavBar from '../components/NavBar.jsx';
import DataState from '../components/DataState.jsx';
import { listSites, createSite, rotateKey, revokeKey, getSnippet } from '../services/sites.service.js';
import { messageFromError } from '../services/errors.js';

export default function SitesPage() {
  const [sites, setSites] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [name, setName] = useState('');
  const [domain, setDomain] = useState('');
  const [newKey, setNewKey] = useState(null); // shown once
  const [snippet, setSnippet] = useState(null);

  async function load() {
    setError(null);
    try { setSites(await listSites()); } catch (e) { setError(messageFromError(e)); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, []);

  async function handleCreate(e) {
    e.preventDefault();
    try {
      const res = await createSite(name, domain);
      setNewKey(res.apiKey);
      setName(''); setDomain('');
      setSnippet(await getSnippet(res.site.id));
      load();
    } catch (err) { setError(messageFromError(err)); }
  }

  return (
    <div className="app-shell">
      <NavBar />
      <main className="content">
        <h2>Sites</h2>
        {error && <p className="error-text">{error}</p>}

        <section className="panel">
          <h3>Onboard a new site</h3>
          <form className="filters" onSubmit={handleCreate}>
            <input placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} required />
            <input placeholder="Domain (optional)" value={domain} onChange={(e) => setDomain(e.target.value)} />
            <button type="submit">Create + issue key</button>
          </form>
          {newKey && (
            <div className="panel" style={{ marginTop: '1rem' }}>
              <p className="error-text">Copy this API key now — it is shown only once:</p>
              <pre className="code-block">{newKey.apiKey}</pre>
              {snippet && (
                <>
                  <h4>Send your first event</h4>
                  <pre className="code-block">{snippet.curl}</pre>
                </>
              )}
            </div>
          )}
        </section>

        <DataState loading={loading} error={error} empty={(sites ?? []).length === 0} emptyText="No sites.">
          <table className="data-table">
            <thead><tr><th>ID</th><th>Name</th><th>Domain</th><th>Status</th><th>Last event</th><th>Connection</th><th></th></tr></thead>
            <tbody>
              {(sites ?? []).map((s) => (
                <tr key={s.id}>
                  <td>{s.id}</td><td>{s.name}</td><td>{s.domain || '—'}</td><td>{s.status}</td>
                  <td>{s.lastEventAt ? new Date(s.lastEventAt).toLocaleString() : 'never'}</td>
                  <td>
                    {s.lastEventAt
                      ? (s.silenced ? <span className="status-badge" style={{ background: '#9a6700' }}>silent</span>
                        : <span className="status-badge" style={{ background: '#1a7f37' }}>verified ✓</span>)
                      : <span className="muted">awaiting first event</span>}
                  </td>
                  <td>
                    <button className="ghost" onClick={async () => { try { setNewKey(await rotateKey(s.id)); } catch (e) { setError(messageFromError(e)); } }}>Rotate key</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </DataState>
      </main>
    </div>
  );
}
