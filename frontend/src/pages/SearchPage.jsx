import { useCallback, useEffect, useState } from 'react';
import DataState from '../components/DataState.jsx';
import SeverityBadge from '../components/SeverityBadge.jsx';
import { listLogSources } from '../services/logsources.service.js';
import { exportEventsCsv, getSearchFields, searchEvents } from '../services/search.service.js';
import { useAuth } from '../context/AuthContext.jsx';
import { messageFromError } from '../services/errors.js';

const RANGES = [
  { value: '1h', label: 'Last hour', ms: 3600e3 },
  { value: '24h', label: 'Last 24 hours', ms: 24 * 3600e3 },
  { value: '7d', label: 'Last 7 days', ms: 7 * 24 * 3600e3 },
  { value: '30d', label: 'Last 30 days', ms: 30 * 24 * 3600e3 },
  { value: 'all', label: 'All time', ms: null },
];
const EXAMPLES = [
  "sourceIp = '10.0.0.5' AND outcome = 'FAILURE'",
  "eventType IN ('FAILED_LOGIN', 'SUSPICIOUS_LOGIN') AND NOT country = 'US'",
  "resource LIKE '/admin*'",
  "user = 'root' OR user = 'admin'",
];
const EMPTY = { range: '24h', sourceId: '', ip: '', user: '', eventType: '', outcome: '' };

/** QRadar-style Log Activity: filters + a small query language, paginated results, CSV export. */
export default function SearchPage() {
  const { hasRole } = useAuth();
  const [fields, setFields] = useState([]);
  const [sources, setSources] = useState([]);
  const [filters, setFilters] = useState(EMPTY);
  const [query, setQuery] = useState('');
  const [submitted, setSubmitted] = useState({ filters: EMPTY, query: '' });
  const [pageNo, setPageNo] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showHelp, setShowHelp] = useState(false);

  useEffect(() => {
    getSearchFields().then(setFields).catch(() => {});
    if (hasRole('ANALYST', 'ADMIN')) listLogSources().then(setSources).catch(() => {});
  }, [hasRole]);

  const params = useCallback((s) => {
    const range = RANGES.find((r) => r.value === s.filters.range);
    const p = {
      q: s.query || undefined,
      from: range?.ms ? new Date(Date.now() - range.ms).toISOString() : undefined,
      sourceId: s.filters.sourceId || undefined,
      ip: s.filters.ip || undefined,
      user: s.filters.user || undefined,
      eventType: s.filters.eventType || undefined,
      outcome: s.filters.outcome || undefined,
    };
    return Object.fromEntries(Object.entries(p).filter(([, v]) => v !== undefined));
  }, []);

  useEffect(() => {
    let active = true;
    setLoading(true);
    searchEvents({ ...params(submitted), page: pageNo, size: 50 })
      .then((r) => { if (active) { setResult(r); setError(null); } })
      .catch((e) => active && setError(messageFromError(e)))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [submitted, pageNo, params]);

  function run(e) {
    e?.preventDefault();
    setPageNo(0);
    setSubmitted({ filters, query });
  }

  async function exportCsv() {
    try { await exportEventsCsv(params(submitted)); } catch (e) { setError(messageFromError(e)); }
  }

  const types = fields.find((f) => f.name === 'eventType')?.values ?? [];
  const rows = result?.content ?? [];

  return (
    <>
      <h2>Event Search</h2>
      <form className="panel" onSubmit={run}>
        <div className="filters">
          <select value={filters.range} onChange={(e) => setFilters({ ...filters, range: e.target.value })} aria-label="Time range">
            {RANGES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
          </select>
          {sources.length > 0 && (
            <select value={filters.sourceId} onChange={(e) => setFilters({ ...filters, sourceId: e.target.value })} aria-label="Log source">
              <option value="">Any log source</option>
              {sources.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          )}
          <input placeholder="Source IP" value={filters.ip} maxLength={45} onChange={(e) => setFilters({ ...filters, ip: e.target.value })} />
          <input placeholder="User" value={filters.user} maxLength={100} onChange={(e) => setFilters({ ...filters, user: e.target.value })} />
          <select value={filters.eventType} onChange={(e) => setFilters({ ...filters, eventType: e.target.value })} aria-label="Event type">
            <option value="">Any event type</option>
            {types.map((t) => <option key={t}>{t}</option>)}
          </select>
          <select value={filters.outcome} onChange={(e) => setFilters({ ...filters, outcome: e.target.value })} aria-label="Outcome">
            <option value="">Any outcome</option>
            <option>SUCCESS</option><option>FAILURE</option><option>UNKNOWN</option>
          </select>
        </div>
        <div className="filters">
          <input className="query-input" style={{ flex: 1, minWidth: 260, fontFamily: 'var(--font-mono, monospace)' }}
            placeholder="Query, e.g. sourceIp = '10.0.0.5' AND outcome = 'FAILURE'" value={query} maxLength={1000}
            onChange={(e) => setQuery(e.target.value)} aria-label="Query" />
          <button type="submit">Search</button>
          <button type="button" className="ghost" onClick={() => { setFilters(EMPTY); setQuery(''); setPageNo(0); setSubmitted({ filters: EMPTY, query: '' }); }}>Clear</button>
          <button type="button" className="ghost" onClick={exportCsv} disabled={!result?.totalElements}>Export CSV</button>
          <button type="button" className="ghost" onClick={() => setShowHelp((v) => !v)} aria-expanded={showHelp}>Query help</button>
        </div>
        {showHelp && (
          <div className="muted small">
            <p>Combine <code>field op value</code> with <code>AND</code>, <code>OR</code>, <code>NOT</code> and parentheses.
              Operators: <code>= != &gt; &gt;= &lt; &lt;= LIKE CONTAINS IN (…)</code>. <code>*</code> is the LIKE wildcard;
              time values are ISO-8601 UTC.</p>
            <p>Fields: {fields.map((f) => <code key={f.name} style={{ marginRight: 6 }}>{f.name}</code>)}</p>
            <div className="chip-row">
              {EXAMPLES.map((ex) => (
                <button type="button" key={ex} className="evidence-chip" onClick={() => setQuery(ex)}>{ex}</button>
              ))}
            </div>
          </div>
        )}
      </form>

      {error && <p className="error-text">{error}</p>}
      <DataState loading={loading && !result} error={null} empty={rows.length === 0} emptyText="No events match.">
        <p className="muted small">{result?.totalElements?.toLocaleString()} event(s){loading ? ' · refreshing…' : ''}</p>
        <table className="data-table">
          <thead><tr><th>Time</th><th>Type</th><th>Outcome</th><th>Severity</th><th>Source IP</th><th>User</th><th>Resource</th><th>Country</th></tr></thead>
          <tbody>
            {rows.map((e) => (
              <tr key={e.id} title={e.rawPayload || ''}>
                <td className="small">{new Date(e.eventTimestamp).toLocaleString()}</td>
                <td><code>{e.eventType}</code></td>
                <td className={e.outcome === 'FAILURE' ? 'error-text' : ''}>{e.outcome}</td>
                <td><SeverityBadge severity={e.severity} /></td>
                <td>{e.sourceIp || '—'}</td>
                <td>{e.username || '—'}</td>
                <td className="small">{e.resource || '—'}</td>
                <td>{e.geoCountry || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {result && result.totalPages > 1 && (
          <div className="filters">
            <button className="ghost" disabled={pageNo === 0} onClick={() => setPageNo((p) => p - 1)}>← Prev</button>
            <span className="muted small">Page {pageNo + 1} of {result.totalPages}</span>
            <button className="ghost" disabled={pageNo + 1 >= result.totalPages} onClick={() => setPageNo((p) => p + 1)}>Next →</button>
          </div>
        )}
      </DataState>
    </>
  );
}
