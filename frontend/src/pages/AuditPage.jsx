import { Fragment, useCallback, useEffect, useState } from 'react';
import api from '../services/api.js';
import { messageFromError } from '../services/errors.js';

/** Tamper-evident audit log (hash chain) with an integrity check. Admin only. */
export default function AuditPage() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [verify, setVerify] = useState(null);
  const [open, setOpen] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try { setData((await api.get('/audit-logs', { params: { page, size: 25 } })).data.data); setError(null); }
    catch (e) { setError(messageFromError(e)); }
  }, [page]);
  useEffect(() => { load(); }, [load]);

  async function runVerify() {
    try { setVerify((await api.get('/audit-logs/verify')).data.data); } catch (e) { setError(messageFromError(e)); }
  }

  return (
    <>
      <h2>Audit Log</h2>
      <p className="subtitle">Every security-relevant change (logins, rule edits, response actions, site connections…) is chained by hash, so editing or deleting a row breaks the chain.</p>
      {error && <p className="error-text">{error}</p>}
      <div className="filters">
        <button onClick={runVerify}>Verify integrity</button>
        {verify && (verify.valid ? <span className="ai-badge badge-valid">Chain intact</span>
          : <span className="ai-badge badge-rejected">Broken at entry #{verify.firstBrokenId}</span>)}
      </div>
      {!data ? <p className="muted">Loading…</p> : data.content.length === 0 ? <p className="muted">No audit entries yet.</p> : (
        <>
          <table className="data-table">
            <thead><tr><th>#</th><th>Time</th><th>Action</th><th>Entity</th><th>Actor</th><th>IP</th></tr></thead>
            <tbody>{data.content.map((a) => (
              <Fragment key={a.id}>
                <tr onClick={() => setOpen(open === a.id ? null : a.id)} style={{ cursor: 'pointer' }}>
                  <td>{a.id}</td><td>{new Date(a.createdAt).toLocaleString()}</td><td><code>{a.action}</code></td>
                  <td>{a.entityType}{a.entityId != null ? ` #${a.entityId}` : ''}</td><td>{a.actorId ?? 'system'}</td><td>{a.ipAddress || '—'}</td>
                </tr>
                {open === a.id && <tr><td colSpan={6}><pre className="code-block" style={{ whiteSpace: 'pre-wrap' }}>{a.details || '—'}</pre>
                  <div className="muted small">hash {a.entryHash?.slice(0, 16)}… · prev {a.prevHash ? `${a.prevHash.slice(0, 16)}…` : '—'}</div></td></tr>}
              </Fragment>
            ))}</tbody>
          </table>
          <div className="filters">
            <button className="ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>← Newer</button>
            <span className="muted small">Page {page + 1} of {Math.max(1, data.totalPages ?? 1)}</span>
            <button className="ghost" disabled={data.totalPages != null && page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Older →</button>
          </div>
        </>
      )}
    </>
  );
}
