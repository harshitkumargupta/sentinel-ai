import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api.js';
import { useAuth } from '../context/AuthContext.jsx';
import { messageFromError } from '../services/errors.js';

/** All vulnerability findings across assets; import a scan CSV, mark fixed / reopen. */
export default function VulnerabilitiesPage() {
  const { hasRole } = useAuth();
  const canEdit = hasRole('ANALYST', 'ADMIN');
  const [rows, setRows] = useState(null);
  const [error, setError] = useState(null);
  const [filter, setFilter] = useState({ status: 'OPEN', severity: '', q: '' });
  const [result, setResult] = useState(null);

  const load = useCallback(async () => {
    try { setRows((await api.get('/vulnerabilities')).data.data); setError(null); }
    catch (e) { setError(messageFromError(e)); setRows([]); }
  }, []);
  useEffect(() => { load(); }, [load]);

  async function upload(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    const fd = new FormData(); fd.append('file', file);
    try { setResult((await api.post('/vulnerabilities/import', fd)).data.data); load(); }
    catch (err) { setError(messageFromError(err)); }
    finally { e.target.value = ''; }
  }
  async function setStatus(v, status) {
    try { await api.patch(`/vulnerabilities/${v.id}/status`, { status }); load(); }
    catch (err) { setError(messageFromError(err)); }
  }

  const shown = useMemo(() => (rows ?? []).filter((v) => (!filter.status || v.status === filter.status)
    && (!filter.severity || v.severity === filter.severity)
    && (!filter.q || `${v.cveId} ${v.asset} ${v.description || ''}`.toLowerCase().includes(filter.q.toLowerCase()))), [rows, filter]);

  return (
    <>
      <h2>Vulnerabilities</h2>
      <p className="subtitle">Scanner findings matched to <Link to="/assets">assets</Link>; open HIGH/CRITICAL findings raise incident risk and lower the executive posture score.</p>
      {error && <p className="error-text">{error}</p>}
      <div className="filters">
        <select value={filter.status} onChange={(e) => setFilter({ ...filter, status: e.target.value })} aria-label="Status">
          <option value="">All statuses</option><option value="OPEN">Open</option><option value="FIXED">Fixed</option></select>
        <select value={filter.severity} onChange={(e) => setFilter({ ...filter, severity: e.target.value })} aria-label="Severity">
          <option value="">All severities</option>{['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((s) => <option key={s}>{s}</option>)}</select>
        <input placeholder="Search CVE / asset" value={filter.q} onChange={(e) => setFilter({ ...filter, q: e.target.value })} maxLength={100} />
        {canEdit && <label className="ghost">Import scan CSV <input type="file" accept=".csv,text/csv" onChange={upload} style={{ display: 'none' }} /></label>}
      </div>
      {result && <p className="small">Created {result.created}, updated {result.updated}, unmatched {result.unmatched}{result.errors?.length ? ` — ${result.errors.slice(0, 3).join('; ')}` : ''}</p>}
      {rows === null ? <p className="muted">Loading…</p> : shown.length === 0 ? <p className="muted">No vulnerabilities match. Import <code>samples/vulnerabilities.csv</code> to try it.</p> : (
        <table className="data-table">
          <thead><tr><th>CVE</th><th>Severity</th><th>Asset</th><th>Description</th><th>Status</th>{canEdit && <th></th>}</tr></thead>
          <tbody>{shown.map((v) => (
            <tr key={v.id}><td><code>{v.cveId}</code></td><td>{v.severity}</td><td>{v.asset}</td><td className="small">{v.description || '—'}</td><td>{v.status}</td>
              {canEdit && <td><button className="ghost" onClick={() => setStatus(v, v.status === 'OPEN' ? 'FIXED' : 'OPEN')}>{v.status === 'OPEN' ? 'Mark fixed' : 'Reopen'}</button></td>}</tr>))}
          </tbody>
        </table>
      )}
    </>
  );
}
