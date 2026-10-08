import { useCallback, useEffect, useState } from 'react';
import DataState from '../components/DataState.jsx';
import { Tabs } from '../components/ui/index.js';
import { useAuth } from '../context/AuthContext.jsx';
import {
  listReferenceSets, createReferenceSet, deleteReferenceSet, listReferenceItems, addReferenceItems,
  removeReferenceItem, getThreatIntel, checkThreatIntel,
} from '../services/reference.service.js';
import { messageFromError } from '../services/errors.js';

function SetItems({ set, canEdit, onChanged }) {
  const [items, setItems] = useState(null);
  const [text, setText] = useState('');
  const [note, setNote] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try { setItems(await listReferenceItems(set.id)); } catch (e) { setError(messageFromError(e)); }
  }, [set.id]);
  useEffect(() => { load(); }, [load]);

  async function add(e) {
    e.preventDefault();
    const values = text.split(/[\s,]+/).map((v) => v.trim()).filter(Boolean);
    if (!values.length) return;
    try {
      setResult(await addReferenceItems(set.id, values, note));
      setText('');
      load();
      onChanged();
    } catch (err) { setError(messageFromError(err)); }
  }

  async function remove(item) {
    try { await removeReferenceItem(set.id, item.id); load(); onChanged(); } catch (e) { setError(messageFromError(e)); }
  }

  return (
    <div className="panel">
      {error && <p className="error-text">{error}</p>}
      {canEdit && (
        <form onSubmit={add}>
          <textarea className="config-edit" rows={2} value={text} onChange={(e) => setText(e.target.value)}
            placeholder={set.elementType === 'IP' ? 'IPs or CIDRs, separated by spaces, commas or new lines' : 'Values, one per line'}
            aria-label="Values to add" />
          <div className="filters">
            <input placeholder="Note (optional)" maxLength={255} value={note} onChange={(e) => setNote(e.target.value)} />
            <button type="submit">Add to {set.name}</button>
          </div>
          {result && (
            <p className="small">Added {result.added}, already present {result.duplicates}
              {result.invalid.length > 0 && <span className="error-text">, invalid: {result.invalid.join(', ')}</span>}</p>
          )}
        </form>
      )}
      <DataState loading={!items && !error} error={null} empty={(items?.content ?? []).length === 0} emptyText="Empty set.">
        <table className="data-table">
          <thead><tr><th>Value</th><th>Note</th><th>Added by</th><th>When</th>{canEdit && <th></th>}</tr></thead>
          <tbody>
            {(items?.content ?? []).map((i) => (
              <tr key={i.id}>
                <td><code>{i.value}</code></td><td className="small">{i.note || '—'}</td>
                <td>{i.addedBy || '—'}</td><td className="small">{new Date(i.createdAt).toLocaleString()}</td>
                {canEdit && <td><button className="ghost" onClick={() => remove(i)}>Remove</button></td>}
              </tr>
            ))}
          </tbody>
        </table>
      </DataState>
    </div>
  );
}

function ThreatIntelTab() {
  const [status, setStatus] = useState(null);
  const [ip, setIp] = useState('');
  const [lookup, setLookup] = useState(null);
  const [error, setError] = useState(null);
  useEffect(() => { getThreatIntel().then(setStatus).catch((e) => setError(messageFromError(e))); }, []);

  async function check(e) {
    e.preventDefault();
    try { setLookup(await checkThreatIntel(ip.trim())); setError(null); } catch (err) { setError(messageFromError(err)); }
  }

  return (
    <>
      <p className="muted small">Offline blocklists loaded at startup from bundled files (sample feeds — illustrative,
        not authoritative). Matches raise an offense's credibility; use <code>TI:&lt;list id&gt;</code> in a building block.</p>
      <DataState loading={!status && !error} error={error} empty={(status?.feeds ?? []).length === 0} emptyText="No lists loaded.">
        <table className="data-table">
          <thead><tr><th>List</th><th>Id (for building blocks)</th><th>IPs</th><th>CIDRs</th><th>Invalid lines</th><th>Origin</th></tr></thead>
          <tbody>
            {(status?.feeds ?? []).map((f) => (
              <tr key={f.id}><td>{f.name}</td><td><code>TI:{f.id}</code></td><td>{f.ips}</td><td>{f.cidrs}</td>
                <td>{f.invalidLines}</td><td>{f.origin}</td></tr>
            ))}
          </tbody>
        </table>
        {status?.loadedAt && <p className="muted small">Loaded {new Date(status.loadedAt).toLocaleString()}</p>}
      </DataState>
      <form className="filters" onSubmit={check}>
        <input placeholder="Check an IPv4 address" value={ip} maxLength={45} onChange={(e) => setIp(e.target.value)} />
        <button type="submit">Check</button>
      </form>
      {lookup && (lookup.matches.length
        ? <p className="error-text">{lookup.ip} is listed in: {lookup.matches.map((m) => m.list).join(', ')}</p>
        : <p className="muted">{lookup.ip} is not on any loaded list.</p>)}
    </>
  );
}

/** Reference sets (watchlists) usable in rules via building blocks, and the offline threat-intel lists. */
export default function ReferenceSetsPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const canEditItems = hasRole('ANALYST', 'ADMIN');
  const [sets, setSets] = useState(null);
  const [open, setOpen] = useState(null);
  const [form, setForm] = useState({ name: '', elementType: 'IP', description: '' });
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try { setSets(await listReferenceSets()); setError(null); } catch (e) { setError(messageFromError(e)); }
  }, []);
  useEffect(() => { load(); }, [load]);

  async function create(e) {
    e.preventDefault();
    try { await createReferenceSet(form); setForm({ name: '', elementType: 'IP', description: '' }); load(); }
    catch (err) { setError(messageFromError(err)); }
  }

  async function remove(s) {
    if (!window.confirm(`Delete reference set "${s.name}" and its ${s.size} value(s)?`)) return;
    try { await deleteReferenceSet(s.id); if (open === s.id) setOpen(null); load(); } catch (e) { setError(messageFromError(e)); }
  }

  const setsTab = (
    <>
      {error && <p className="error-text">{error}</p>}
      <p className="muted small">Use a set in a rule with a building block condition <code>field IN_REFERENCE_SET [set name]</code>.</p>
      <DataState loading={!sets && !error} error={null} empty={(sets ?? []).length === 0} emptyText="No reference sets.">
        <table className="data-table">
          <thead><tr><th>Name</th><th>Holds</th><th>Values</th><th>Updated</th><th></th></tr></thead>
          <tbody>
            {(sets ?? []).map((s) => (
              <tr key={s.id}>
                <td><strong>{s.name}</strong>{s.description && <div className="muted small">{s.description}</div>}</td>
                <td>{s.elementType}</td><td>{s.size}</td>
                <td className="small">{new Date(s.updatedAt).toLocaleString()}</td>
                <td>
                  <button className="ghost" onClick={() => setOpen(open === s.id ? null : s.id)}>{open === s.id ? 'Close' : 'Open'}</button>
                  {isAdmin && <button className="ghost" onClick={() => remove(s)}>Delete</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </DataState>
      {open && sets?.find((s) => s.id === open) && (
        <SetItems set={sets.find((s) => s.id === open)} canEdit={canEditItems} onChanged={load} />
      )}
      {isAdmin && (
        <form className="panel filters" onSubmit={create}>
          <input placeholder="New set name" required maxLength={100} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <select value={form.elementType} onChange={(e) => setForm({ ...form, elementType: e.target.value })} aria-label="Element type">
            <option value="IP">IP / CIDR</option><option value="USERNAME">Username</option><option value="TEXT">Text</option>
          </select>
          <input placeholder="Description" maxLength={500} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          <button type="submit">Create set</button>
        </form>
      )}
    </>
  );

  return (
    <>
      <h2>Reference Sets &amp; Threat Intel</h2>
      <Tabs tabs={[
        { id: 'sets', label: 'Reference sets', content: setsTab },
        { id: 'ti', label: 'Threat intel (offline)', content: <ThreatIntelTab /> },
      ]} />
    </>
  );
}
