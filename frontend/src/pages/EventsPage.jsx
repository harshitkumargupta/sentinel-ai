import { useCallback, useEffect, useState } from 'react';
import DataState from '../components/DataState.jsx';
import SeverityBadge from '../components/SeverityBadge.jsx';
import { listEvents } from '../services/events.service.js';
import { nlSearch } from '../services/ai.service.js';
import { messageFromError } from '../services/errors.js';

const EVENT_TYPES = ['FAILED_LOGIN', 'BRUTE_FORCE', 'SUSPICIOUS_LOGIN', 'API_ABUSE',
  'ABNORMAL_ACCESS', 'HONEYTOKEN_ACCESS', 'OTHER', 'PROMPT_INJECTION'];
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

export default function EventsPage() {
  const [filters, setFilters] = useState({ ip: '', user: '', type: '', severity: '', from: '', to: '' });
  const [page, setPage] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selected, setSelected] = useState(null);

  // Natural-language search: when a result is set it replaces the listing (and pauses live refresh).
  const [nlQuery, setNlQuery] = useState('');
  const [nlResult, setNlResult] = useState(null);
  const [nlError, setNlError] = useState(null);
  const [nlBusy, setNlBusy] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const clean = Object.fromEntries(Object.entries(filters).filter(([, v]) => v));
      setPage(await listEvents({ ...clean, size: 25 }));
    } catch (e) {
      setError(messageFromError(e));
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => {
    if (nlResult) return undefined; // paused while showing NL results
    load();
    const t = setInterval(load, 10000); // live refresh
    return () => clearInterval(t);
  }, [load, nlResult]);

  async function runNlSearch(e) {
    e?.preventDefault();
    if (!nlQuery.trim()) return;
    setNlBusy(true);
    setNlError(null);
    try {
      setNlResult(await nlSearch(nlQuery));
    } catch (err) {
      setNlError(messageFromError(err));
      setNlResult(null);
    } finally {
      setNlBusy(false);
    }
  }

  function clearNl() {
    setNlResult(null);
    setNlError(null);
    setNlQuery('');
  }

  const rows = nlResult ? nlResult.results : (page?.content ?? []);

  return (
    <>
        <h2>Events</h2>

        <form className="filters" onSubmit={runNlSearch}>
          <input className="nl-input" placeholder="Ask in plain language, e.g. 'critical brute force from 203.0.113.5 last 7 days'"
                 value={nlQuery} onChange={(e) => setNlQuery(e.target.value)} />
          <button type="submit" disabled={nlBusy}>{nlBusy ? 'Searching…' : 'Ask AI'}</button>
          {nlResult && <button type="button" className="ghost" onClick={clearNl}>Clear</button>}
        </form>
        {nlError && <p className="error-text">{nlError}</p>}
        {nlResult && (
          <div className="chip-row">
            <span className="muted small">Interpreted as:</span>
            {Object.entries(nlResult.interpretedFilter).map(([k, v]) => (
              <span key={k} className="chip">{k}: {v}</span>
            ))}
            <span className="muted small">{nlResult.total} match(es)</span>
          </div>
        )}

        <div className="filters">
          <input placeholder="IP" value={filters.ip} onChange={(e) => setFilters({ ...filters, ip: e.target.value })} />
          <input placeholder="User" value={filters.user} onChange={(e) => setFilters({ ...filters, user: e.target.value })} />
          <select value={filters.type} onChange={(e) => setFilters({ ...filters, type: e.target.value })}>
            <option value="">Any type</option>
            {EVENT_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
          <select value={filters.severity} onChange={(e) => setFilters({ ...filters, severity: e.target.value })}>
            <option value="">Any severity</option>
            {SEVERITIES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
          <button onClick={load}>Refresh</button>
        </div>

        <DataState loading={loading} error={error} empty={rows.length === 0} emptyText="No events match.">
          <table className="data-table">
            <thead>
              <tr><th>ID</th><th>Type</th><th>Severity</th><th>User</th><th>IP</th><th>Country</th><th>When</th></tr>
            </thead>
            <tbody>
              {rows.map((e) => (
                <tr key={e.id} onClick={() => setSelected(e)} className="clickable">
                  <td>{e.id}</td>
                  <td>{e.eventType}</td>
                  <td><SeverityBadge severity={e.severity} /></td>
                  <td>{e.username || '—'}</td>
                  <td>{e.sourceIp || '—'}</td>
                  <td>{e.geoCountry || '—'}</td>
                  <td>{new Date(e.eventTimestamp).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </DataState>

      {selected && (
        <div className="drawer" onClick={() => setSelected(null)}>
          <div className="drawer-panel" onClick={(ev) => ev.stopPropagation()}>
            <div className="drawer-head">
              <h3>Event #{selected.id}</h3>
              <button className="ghost" onClick={() => setSelected(null)}>Close</button>
            </div>
            <pre className="code-block">{JSON.stringify(selected, null, 2)}</pre>
          </div>
        </div>
      )}
    </>
  );
}
