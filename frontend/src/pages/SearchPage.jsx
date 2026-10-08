import { useCallback, useEffect, useState } from 'react';
import DataState from '../components/DataState.jsx';
import SeverityBadge from '../components/SeverityBadge.jsx';
import { listLogSources } from '../services/logsources.service.js';
import {
  exportEventsCsv, getSearchFields, searchEvents, listSavedSearches, saveSearch, deleteSavedSearch,
  translatePlainEnglish, topValues,
} from '../services/search.service.js';
import { useSearchParams } from 'react-router-dom';
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
const NL_EXAMPLES = [
  'failed logins in the last hour', 'successful logins yesterday', 'failed logins from 45.33.12.7',
  'everything for user alice today', 'brute force in the last 24 hours', 'top 5 IPs with failed logins this week',
  'port scan from 185.220.101.4', 'logins from Russia in the last 7 days', 'high severity alerts last 2 hours',
  'top 10 users in the last 30 days',
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
  const [saved, setSaved] = useState([]);
  const [nl, setNl] = useState('');
  const [nlResult, setNlResult] = useState(null);
  const [top, setTop] = useState(null);

  async function ask(text) {
    const phrase = (text ?? nl).trim();
    if (!phrase) return;
    setNl(phrase);
    try {
      const r = await translatePlainEnglish(phrase);
      setNlResult(r);
      setTop(null);
      if (!r.understood) return;
      // Time lives in the generated query, so search over all time and let the user edit it.
      const f = { ...EMPTY, range: 'all' };
      setFilters(f);
      setQuery(r.query || '');
      setPageNo(0);
      setSubmitted({ filters: f, query: r.query || '' });
      if (r.top) setTop({ ...r.top, rows: await topValues(r.top.field, r.top.n, r.query) });
    } catch (e) { setError(messageFromError(e)); }
  }
  const [urlParams] = useSearchParams();

  const loadSaved = useCallback(() => { listSavedSearches().then(setSaved).catch(() => {}); }, []);
  useEffect(() => { loadSaved(); }, [loadSaved]);

  function applySaved(s) {
    const f = { ...EMPTY, ...Object.fromEntries(Object.entries(s.filters || {}).filter(([, v]) => v != null)) };
    setFilters(f);
    setQuery(s.query || '');
    setPageNo(0);
    setSubmitted({ filters: f, query: s.query || '' });
  }

  // Opened from a dashboard widget: /search?saved=<id>
  useEffect(() => {
    const id = Number(urlParams.get('saved'));
    const s = saved.find((x) => x.id === id);
    if (s) applySaved(s);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [urlParams, saved]);

  async function saveCurrent() {
    const name = window.prompt('Name this search');
    if (!name) return;
    const f = Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== ''));
    try {
      await saveSearch(null, { name, query: query || null, filters: f, pinned: false });
      loadSaved();
    } catch (e) { setError(messageFromError(e)); }
  }

  async function togglePin(s) {
    try { await saveSearch(s.id, { name: s.name, query: s.query, filters: s.filters, pinned: !s.pinned }); loadSaved(); }
    catch (e) { setError(messageFromError(e)); }
  }

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
      <section className="panel">
        <form className="filters" onSubmit={(e) => { e.preventDefault(); ask(); }}>
          <input style={{ flex: 1, minWidth: 260 }} maxLength={300} placeholder="Ask in plain English, e.g. failed logins from 45.33.12.7 in the last hour"
            value={nl} onChange={(e) => setNl(e.target.value)} aria-label="Ask in plain English" />
          <button type="submit">Ask</button>
        </form>
        <div className="chip-row">
          {NL_EXAMPLES.map((ex) => <button key={ex} type="button" className="evidence-chip" onClick={() => ask(ex)}>{ex}</button>)}
        </div>
        {nlResult && (nlResult.understood
          ? <p className="small">{nlResult.message}. Generated query (edit below and press Search): <code>{nlResult.query || '(all events)'}</code></p>
          : <p className="error-text small">{nlResult.message}</p>)}
        {top && (
          <table className="data-table" style={{ maxWidth: 480 }}>
            <thead><tr><th>Top {top.n} {top.field}</th><th>Events</th></tr></thead>
            <tbody>{top.rows.map((r) => <tr key={r.value}><td><code>{r.value}</code></td><td>{r.count}</td></tr>)}</tbody>
          </table>
        )}
        <p className="muted small">Offline and rule-based — no AI service. Times are UTC.</p>
      </section>

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
          <button type="button" className="ghost" onClick={saveCurrent}>Save search</button>
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

      {saved.length > 0 && (
        <section className="panel">
          <h3>Saved searches</h3>
          <table className="data-table">
            <thead><tr><th>Name</th><th>Query</th><th>Range</th><th></th></tr></thead>
            <tbody>
              {saved.map((s) => (
                <tr key={s.id}>
                  <td>{s.name}</td><td className="small"><code>{s.query || '—'}</code></td><td>{s.filters?.range || '—'}</td>
                  <td>
                    <button className="ghost" onClick={() => applySaved(s)}>Run</button>
                    <button className="ghost" onClick={() => togglePin(s)} aria-pressed={s.pinned}>{s.pinned ? 'Unpin' : 'Pin to dashboard'}</button>
                    <button className="ghost" onClick={async () => { try { await deleteSavedSearch(s.id); loadSaved(); } catch (e) { setError(messageFromError(e)); } }}>Delete</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

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
