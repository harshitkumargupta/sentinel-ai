import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import DataState from '../components/DataState.jsx';
import SeverityBadge from '../components/SeverityBadge.jsx';
import { listIncidents } from '../services/incidents.service.js';
import { messageFromError } from '../services/errors.js';

const STATUSES = ['OPEN', 'INVESTIGATING', 'CONTAINED', 'RESOLVED', 'FALSE_POSITIVE'];
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

export default function IncidentsPage() {
  const [filters, setFilters] = useState({ status: '', severity: '' });
  const [page, setPage] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    let active = true;
    setLoading(true);
    const clean = Object.fromEntries(Object.entries(filters).filter(([, v]) => v));
    listIncidents({ ...clean, size: 50 })
      .then((d) => active && setPage(d))
      .catch((e) => active && setError(messageFromError(e)))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [filters]);

  const rows = page?.content ?? [];

  return (
    <>
        <h2>Incidents</h2>
        <div className="filters">
          <select value={filters.status} onChange={(e) => setFilters({ ...filters, status: e.target.value })}>
            <option value="">Any status</option>
            {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
          <select value={filters.severity} onChange={(e) => setFilters({ ...filters, severity: e.target.value })}>
            <option value="">Any severity</option>
            {SEVERITIES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>

        <DataState loading={loading} error={error} empty={rows.length === 0} emptyText="No incidents.">
          <table className="data-table">
            <thead>
              <tr><th>ID</th><th>Title</th><th>Status</th><th>Severity</th><th>Risk</th><th>Created</th></tr>
            </thead>
            <tbody>
              {rows.map((i) => (
                <tr key={i.id} className="clickable" onClick={() => navigate(`/incidents/${i.id}`)}>
                  <td>{i.id}</td>
                  <td>{i.title}</td>
                  <td>{i.status}</td>
                  <td><SeverityBadge severity={i.severity} /></td>
                  <td>
                    <span className="risk-bar-wrap">
                      <span className="risk-bar" style={{ width: `${i.riskScore ?? 0}%` }} />
                    </span>
                    <span className="small">{i.riskScore ?? 0}</span>
                  </td>
                  <td>{new Date(i.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </DataState>
    </>
  );
}
