import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import DataState from '../components/DataState.jsx';
import SeverityBadge from '../components/SeverityBadge.jsx';
import { listIncidents } from '../services/incidents.service.js';
import { listAssignees } from '../services/offenses.service.js';
import { PRIORITIES, STATUSES, statusLabel } from '../services/caseLabels.js';
import { messageFromError } from '../services/errors.js';
import { useLiveRefresh } from '../hooks/useLiveRefresh.js';

const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

export default function IncidentsPage() {
  const [filters, setFilters] = useState({ status: '', severity: '', priority: '', assignee: '' });
  const [assignees, setAssignees] = useState([]);
  useEffect(() => { listAssignees().then(setAssignees).catch(() => setAssignees([])); }, []);
  const [page, setPage] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const navigate = useNavigate();
  const [tick, setTick] = useState(0);
  useLiveRefresh(() => setTick((t) => t + 1), 10000);

  useEffect(() => {
    let active = true;
    if (tick === 0) setLoading(true);
    const { assignee, ...rest } = filters;
    const clean = Object.fromEntries(Object.entries(rest).filter(([, v]) => v));
    if (assignee === 'none') clean.unassigned = true;
    else if (assignee) clean.assigneeId = assignee;
    listIncidents({ ...clean, size: 50 })
      .then((d) => active && setPage(d))
      .catch((e) => active && setError(messageFromError(e)))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [filters, tick]);

  const rows = page?.content ?? [];

  return (
    <>
        <h2>Incidents (cases)</h2>
        <div className="filters">
          <select value={filters.status} onChange={(e) => setFilters({ ...filters, status: e.target.value })}>
            <option value="">Any status</option>
            {STATUSES.map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
          </select>
          <select value={filters.priority} onChange={(e) => setFilters({ ...filters, priority: e.target.value })} aria-label="Priority">
            <option value="">Any priority</option>
            {PRIORITIES.map((p) => <option key={p}>{p}</option>)}
          </select>
          <select value={filters.assignee} onChange={(e) => setFilters({ ...filters, assignee: e.target.value })} aria-label="Assignee">
            <option value="">Any assignee</option>
            <option value="none">Unassigned</option>
            {assignees.map((a) => <option key={a.id} value={a.id}>{a.username}</option>)}
          </select>
          <select value={filters.severity} onChange={(e) => setFilters({ ...filters, severity: e.target.value })}>
            <option value="">Any severity</option>
            {SEVERITIES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>

        <DataState loading={loading} error={error} empty={rows.length === 0} emptyText="No incidents.">
          <table className="data-table">
            <thead>
              <tr><th>ID</th><th>Title</th><th>Status</th><th>Priority</th><th>Severity</th><th>Assigned</th><th>Risk</th><th>Created</th></tr>
            </thead>
            <tbody>
              {rows.map((i) => (
                <tr key={i.id} className="clickable" onClick={() => navigate(`/incidents/${i.id}`)}>
                  <td>{i.id}</td>
                  <td>{i.title}</td>
                  <td>{statusLabel(i.status)}</td>
                  <td>{i.priority}</td>
                  <td><SeverityBadge severity={i.severity} /></td>
                  <td>{i.assignedTo || <span className="muted">—</span>}</td>
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
