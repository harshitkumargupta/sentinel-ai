import { useCallback, useEffect, useState } from 'react';
import DataState from '../components/DataState.jsx';
import SeverityBadge from '../components/SeverityBadge.jsx';
import MitreChip from '../components/MitreChip.jsx';
import { listAlerts } from '../services/alerts.service.js';
import { messageFromError } from '../services/errors.js';
import { useLiveRefresh } from '../hooks/useLiveRefresh.js';

export default function AlertsPage() {
  const [page, setPage] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      setPage(await listAlerts({ size: 50 }));
      setError(null);
    } catch (e) {
      setError(messageFromError(e));
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { load(); }, [load]);
  useLiveRefresh(load, 10000); // live: interval + immediate reload on Demo Center / upload / action

  const rows = page?.content ?? [];

  return (
    <>
        <h2>Alerts</h2>
        <DataState loading={loading} error={error} empty={rows.length === 0} emptyText="No alerts yet.">
          <table className="data-table">
            <thead>
              <tr><th>ID</th><th>Rule</th><th>Severity</th><th>MITRE</th><th>Message</th><th>When</th></tr>
            </thead>
            <tbody>
              {rows.map((a) => (
                <tr key={a.id}>
                  <td>{a.id}</td>
                  <td>{a.ruleType}</td>
                  <td><SeverityBadge severity={a.severity} /></td>
                  <td><MitreChip technique={a.mitreTechnique} /></td>
                  <td>{a.message}</td>
                  <td>{new Date(a.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </DataState>
    </>
  );
}
