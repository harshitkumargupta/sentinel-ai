import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import DataState from '../components/DataState.jsx';
import SeverityBadge from '../components/SeverityBadge.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../components/ui/index.js';
import {
  ASSET_TYPES, ENVIRONMENTS, CRITICALITIES, listAssets, getAsset, saveAsset, deleteAsset, importAssets, relinkAssets,
} from '../services/assets.service.js';
import { statusLabel } from '../services/caseLabels.js';
import { messageFromError } from '../services/errors.js';

const EMPTY = { hostname: '', ip: '', owner: '', type: 'SERVER', environment: 'PRODUCTION', criticality: 'MEDIUM', description: '' };

/** Asset inventory: hosts/IPs with owner, type, environment and criticality; CSV import; linked incidents. */
export default function AssetsPage() {
  const { hasRole } = useAuth();
  const canEdit = hasRole('ANALYST', 'ADMIN');
  const isAdmin = hasRole('ADMIN');
  const { push } = useToast();
  const [assets, setAssets] = useState(null);
  const [error, setError] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [editingId, setEditingId] = useState(null);
  const [open, setOpen] = useState(null); // { asset, incidents }
  const [importResult, setImportResult] = useState(null);

  const load = useCallback(async () => {
    try { setAssets(await listAssets()); setError(null); } catch (e) { setError(messageFromError(e)); }
  }, []);
  useEffect(() => { load(); }, [load]);

  async function submit(e) {
    e.preventDefault();
    try {
      await saveAsset(editingId, form);
      push(editingId ? 'Asset updated' : 'Asset added', { variant: 'success' });
      setForm(EMPTY); setEditingId(null); load();
    } catch (err) { setError(messageFromError(err)); }
  }

  function edit(a) {
    setEditingId(a.id);
    setForm({ hostname: a.hostname || '', ip: a.ip || '', owner: a.owner || '', type: a.type,
      environment: a.environment, criticality: a.criticality, description: a.description || '' });
  }

  async function remove(a) {
    if (!window.confirm(`Delete asset ${a.hostname || a.ip}? Its events are kept but unlinked.`)) return;
    try { await deleteAsset(a.id); if (open?.asset.id === a.id) setOpen(null); load(); } catch (e) { setError(messageFromError(e)); }
  }

  async function onImport(e) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    try { setImportResult(await importAssets(file)); load(); } catch (err) { setError(messageFromError(err)); }
  }

  return (
    <>
      <h2>Assets</h2>
      <p className="subtitle">Inventoried hosts and IPs. Events are linked by hostname or IP, and an asset's criticality
        raises the risk and magnitude of incidents that touch it.</p>
      {error && <p className="error-text">{error}</p>}

      <DataState loading={!assets && !error} error={null} empty={(assets ?? []).length === 0} emptyText="No assets yet.">
        <table className="data-table">
          <thead><tr><th>Criticality</th><th>Hostname</th><th>IP</th><th>Owner</th><th>Type</th><th>Environment</th>
            <th>Events</th><th>Open incidents</th><th>Open vulns</th><th></th></tr></thead>
          <tbody>
            {(assets ?? []).map((a) => (
              <tr key={a.id}>
                <td><SeverityBadge severity={a.criticality} /></td>
                <td><strong>{a.hostname || '—'}</strong>{a.description && <div className="muted small">{a.description}</div>}</td>
                <td><code>{a.ip || '—'}</code></td><td>{a.owner || '—'}</td><td>{a.type}</td><td>{a.environment}</td>
                <td>{a.linkedEvents}</td>
                <td className={a.openIncidents > 0 ? 'error-text' : ''}>{a.openIncidents}</td>
                <td className={a.openVulnerabilities > 0 ? 'error-text' : ''}>{a.openVulnerabilities}</td>
                <td>
                  <button className="ghost" onClick={async () => {
                    try { setOpen(open?.asset.id === a.id ? null : await getAsset(a.id)); } catch (e) { setError(messageFromError(e)); }
                  }}>{open?.asset.id === a.id ? 'Close' : 'Details'}</button>
                  {canEdit && <button className="ghost" onClick={() => edit(a)}>Edit</button>}
                  {isAdmin && <button className="ghost" onClick={() => remove(a)}>Delete</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </DataState>

      {open && (
        <section className="panel">
          <h3>{open.asset.hostname || open.asset.ip} — incidents</h3>
          {open.incidents.length === 0 ? <p className="muted">No incidents involve this asset.</p> : (
            <ul className="breakdown">
              {open.incidents.map((i) => (
                <li key={i.id}><Link to={`/offenses/${i.id}`}>#{i.id} {i.title}</Link>
                  <span><SeverityBadge severity={i.severity} /> {statusLabel(i.status)}</span></li>
              ))}
            </ul>
          )}
        </section>
      )}

      {canEdit && (
        <form className="panel" onSubmit={submit}>
          <h3>{editingId ? 'Edit asset' : 'Add an asset'}</h3>
          <div className="filters">
            <input placeholder="Hostname" maxLength={255} value={form.hostname} onChange={(e) => setForm({ ...form, hostname: e.target.value })} />
            <input placeholder="IPv4" maxLength={45} value={form.ip} onChange={(e) => setForm({ ...form, ip: e.target.value })} />
            <input placeholder="Owner" maxLength={150} value={form.owner} onChange={(e) => setForm({ ...form, owner: e.target.value })} />
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })} aria-label="Type">
              {ASSET_TYPES.map((t) => <option key={t}>{t}</option>)}
            </select>
            <select value={form.environment} onChange={(e) => setForm({ ...form, environment: e.target.value })} aria-label="Environment">
              {ENVIRONMENTS.map((t) => <option key={t}>{t}</option>)}
            </select>
            <select value={form.criticality} onChange={(e) => setForm({ ...form, criticality: e.target.value })} aria-label="Criticality">
              {CRITICALITIES.map((t) => <option key={t}>{t}</option>)}
            </select>
          </div>
          <div className="filters">
            <input style={{ flex: 1 }} placeholder="Description" maxLength={500} value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })} />
            <button type="submit">{editingId ? 'Save' : 'Add asset'}</button>
            {editingId && <button type="button" className="ghost" onClick={() => { setEditingId(null); setForm(EMPTY); }}>Cancel</button>}
          </div>
        </form>
      )}

      {canEdit && (
        <section className="panel">
          <h3>Import from CSV</h3>
          <p className="muted small">Header row with any of: <code>hostname, ip, owner, type, environment, criticality, description</code>.
            Rows update existing assets by hostname (else IP). Max 5,000 rows / 1 MB. A sample is bundled at
            <code> backend/src/main/resources/samples/assets.csv</code>.</p>
          <div className="filters">
            <input type="file" accept=".csv,.txt" onChange={onImport} aria-label="Asset CSV" />
            {isAdmin && <button className="ghost" onClick={async () => {
              try { push(`Linked ${await relinkAssets()} event(s) to assets`, { variant: 'success' }); load(); } catch (e) { setError(messageFromError(e)); }
            }}>Relink stored events</button>}
          </div>
          {importResult && (
            <p className="small">Created {importResult.created}, updated {importResult.updated}, skipped {importResult.skipped},
              linked {importResult.relinkedEvents} stored event(s).
              {importResult.errors.length > 0 && <span className="error-text"> Errors: {importResult.errors.join('; ')}</span>}</p>
          )}
        </section>
      )}
    </>
  );
}
