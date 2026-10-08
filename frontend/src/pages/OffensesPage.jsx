import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import DataState from '../components/DataState.jsx';
import SeverityBadge from '../components/SeverityBadge.jsx';
import MagnitudeBar from '../components/MagnitudeBar.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useLiveRefresh } from '../hooks/useLiveRefresh.js';
import { listOffenses, listAssignees } from '../services/offenses.service.js';
import { messageFromError } from '../services/errors.js';

const STATUSES = ['OPEN', 'INVESTIGATING', 'CONTAINED', 'RESOLVED', 'FALSE_POSITIVE'];

/** QRadar-style Offenses: correlated incidents ranked by magnitude, with filters and live refresh. */
export default function OffensesPage() {
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const [filters, setFilters] = useState({ status: '', assigneeId: '', minMagnitude: '', sort: 'MAGNITUDE' });
  const [pageNo, setPageNo] = useState(0);
  const [data, setData] = useState(null);
  const [assignees, setAssignees] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (hasRole('ANALYST', 'ADMIN')) listAssignees().then(setAssignees).catch(() => {});
  }, [hasRole]);

  const load = useCallback(async () => {
    try {
      const params = Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== ''));
      setData(await listOffenses({ ...params, page: pageNo, size: 25 }));
      setError(null);
    } catch (e) {
      setError(messageFromError(e));
    } finally {
      setLoading(false);
    }
  }, [filters, pageNo]);
  useEffect(() => { setLoading(true); load(); }, [load]);
  useLiveRefresh(load, 10000);

  const set = (patch) => { setPageNo(0); setFilters((f) => ({ ...f, ...patch })); };
  const rows = data?.content ?? [];

  return (
    <>
      <h2>Offenses</h2>
      <p className="subtitle">Correlated incidents ranked by magnitude (severity, relevance, credibility).</p>
      <div className="filters">
        <select value={filters.status} onChange={(e) => set({ status: e.target.value })} aria-label="Status">
          <option value="">Any status</option>
          {STATUSES.map((s) => <option key={s}>{s}</option>)}
        </select>
        {assignees.length > 0 && (
          <select value={filters.assigneeId} onChange={(e) => set({ assigneeId: e.target.value })} aria-label="Assignee">
            <option value="">Any assignee</option>
            {assignees.map((a) => <option key={a.id} value={a.id}>{a.username}</option>)}
          </select>
        )}
        <select value={filters.minMagnitude} onChange={(e) => set({ minMagnitude: e.target.value })} aria-label="Minimum magnitude">
          <option value="">Any magnitude</option>
          {[3, 5, 7, 9].map((m) => <option key={m} value={m}>Magnitude ≥ {m}</option>)}
        </select>
        <select value={filters.sort} onChange={(e) => set({ sort: e.target.value })} aria-label="Sort">
          <option value="MAGNITUDE">Sort: magnitude</option>
          <option value="RECENT">Sort: most recent activity</option>
        </select>
      </div>

      <DataState loading={loading} error={!data && error} empty={rows.length === 0} emptyText="No offenses.">
        <table className="data-table">
          <thead>
            <tr><th>Magnitude</th><th>Offense</th><th>Source</th><th>Categories</th><th>Events</th>
              <th>Log sources</th><th>Status</th><th>Assigned</th><th>Last event</th></tr>
          </thead>
          <tbody>
            {rows.map((o) => (
              <tr key={o.id} className="clickable" onClick={() => navigate(`/offenses/${o.id}`)}>
                <td><MagnitudeBar value={o.magnitude.magnitude} /></td>
                <td><strong>#{o.id}</strong> {o.title} <SeverityBadge severity={o.severity} /></td>
                <td><code>{o.offenseSource}</code></td>
                <td>{o.categories.map((c) => <span key={c} className="chip">{c}</span>)}</td>
                <td>{o.eventCount}</td>
                <td className="small">{o.logSources.join(', ') || '—'}</td>
                <td>{o.status}</td>
                <td>{o.assignedTo || <span className="muted">—</span>}</td>
                <td className="small">{o.lastSeen ? new Date(o.lastSeen).toLocaleString() : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.totalPages > 1 && (
          <div className="filters">
            <button className="ghost" disabled={pageNo === 0} onClick={() => setPageNo((p) => p - 1)}>← Prev</button>
            <span className="muted small">Page {pageNo + 1} of {data.totalPages} · {data.totalElements} offenses</span>
            <button className="ghost" disabled={pageNo + 1 >= data.totalPages} onClick={() => setPageNo((p) => p + 1)}>Next →</button>
          </div>
        )}
      </DataState>
    </>
  );
}
