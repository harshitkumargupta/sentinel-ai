import { useCallback, useEffect, useState } from 'react';
import { useToast } from '../components/ui/index.js';
import SeverityBadge from '../components/SeverityBadge.jsx';
import {
  listChannels, saveChannel, deleteChannel, testChannel, listNotifyRules, saveNotifyRule, deleteNotifyRule, listDeliveries,
} from '../services/notifications.service.js';
import { listRuleTypes } from '../services/rules.service.js';
import { messageFromError } from '../services/errors.js';

const STATUS_CLASS = { SENT: 'badge-valid', MOCKED: 'badge-fallback', FAILED: 'badge-rejected' };

/** Admin: notification channels (in-app / email / webhook), routing rules, test sends and the delivery log. */
export default function NotificationSettingsPage() {
  const { push } = useToast();
  const [channels, setChannels] = useState([]);
  const [rules, setRules] = useState([]);
  const [deliveries, setDeliveries] = useState([]);
  const [ruleTypes, setRuleTypes] = useState([]);
  const [error, setError] = useState(null);
  const [ch, setCh] = useState({ name: '', type: 'EMAIL', target: '' });
  const [rule, setRule] = useState({ name: '', minSeverity: 'HIGH', ruleType: '', onIncidentCreated: true, onEscalation: true, channelIds: [] });

  const load = useCallback(async () => {
    try {
      const [c, r, d] = await Promise.all([listChannels(), listNotifyRules(), listDeliveries()]);
      setChannels(c); setRules(r); setDeliveries(d); setError(null);
    } catch (e) { setError(messageFromError(e)); }
  }, []);
  useEffect(() => { load(); listRuleTypes().then((t) => setRuleTypes([...t])).catch(() => {}); }, [load]);

  const act = async (fn, ok) => { try { await fn(); if (ok) push(ok, { variant: 'success' }); load(); } catch (e) { setError(messageFromError(e)); } };
  const name = (id) => channels.find((c) => c.id === id)?.name || `#${id}`;

  async function test(c) {
    try {
      const d = await testChannel(c.id);
      push(`${c.name}: ${d.status}${d.lastError ? ` — ${d.lastError}` : ''} (${d.attempts} attempt(s))`,
        { variant: d.status === 'FAILED' ? 'error' : 'success' });
      load();
    } catch (e) { setError(messageFromError(e)); }
  }

  return (
    <>
      <h2>Notifications</h2>
      <p className="subtitle">Delivery runs after detection commits, on its own threads, with retries — a failing channel never blocks detection or response.</p>
      {error && <p className="error-text">{error}</p>}

      <section className="panel">
        <h3>Channels</h3>
        <table className="data-table">
          <thead><tr><th>Name</th><th>Type</th><th>Target</th><th>On</th><th></th></tr></thead>
          <tbody>
            {channels.map((c) => (
              <tr key={c.id}>
                <td>{c.name} {c.mock && <span className="ai-badge badge-fallback" title="No SMTP configured — messages are only logged">Mock channel</span>}</td>
                <td>{c.type}</td><td className="small"><code>{c.target || 'all analysts & admins'}</code></td>
                <td><button className="ghost" onClick={() => act(() => saveChannel(c.id, { ...c, enabled: !c.enabled }))}>{c.enabled ? 'On' : 'Off'}</button></td>
                <td><button className="ghost" onClick={() => test(c)}>Test</button>
                  <button className="ghost" onClick={() => act(() => deleteChannel(c.id))}>Delete</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        <form className="filters" onSubmit={(e) => { e.preventDefault(); act(async () => { await saveChannel(null, ch); setCh({ name: '', type: 'EMAIL', target: '' }); }, 'Channel added'); }}>
          <input placeholder="Name" required maxLength={100} value={ch.name} onChange={(e) => setCh({ ...ch, name: e.target.value })} />
          <select value={ch.type} onChange={(e) => setCh({ ...ch, type: e.target.value })} aria-label="Channel type">
            <option value="EMAIL">Email (SMTP if configured, else mock)</option><option value="WEBHOOK">Webhook URL</option><option value="IN_APP">In-app bell</option>
          </select>
          {ch.type !== 'IN_APP' && <input placeholder={ch.type === 'EMAIL' ? 'soc@example.com' : 'https://hooks.example/…'} maxLength={500}
            value={ch.target} onChange={(e) => setCh({ ...ch, target: e.target.value })} required />}
          <button type="submit">Add channel</button>
        </form>
      </section>

      <section className="panel">
        <h3>Rules</h3>
        <table className="data-table">
          <thead><tr><th>Name</th><th>When</th><th>Channels</th><th>On</th><th></th></tr></thead>
          <tbody>
            {rules.map((r) => (
              <tr key={r.id}>
                <td>{r.name}</td>
                <td className="small">severity ≥ <SeverityBadge severity={r.minSeverity} />{r.ruleType && <> · rule {r.ruleType}</>}
                  {' '}· {[r.onIncidentCreated && 'created', r.onEscalation && 'escalated'].filter(Boolean).join(' / ')}</td>
                <td className="small">{r.channelIds.map(name).join(', ')}</td>
                <td><button className="ghost" onClick={() => act(() => saveNotifyRule(r.id, { ...r, enabled: !r.enabled }))}>{r.enabled ? 'On' : 'Off'}</button></td>
                <td><button className="ghost" onClick={() => act(() => deleteNotifyRule(r.id))}>Delete</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        <form className="filters" onSubmit={(e) => { e.preventDefault(); act(() => saveNotifyRule(null, { ...rule, ruleType: rule.ruleType || null }), 'Rule added'); }}>
          <input placeholder="Rule name" required maxLength={100} value={rule.name} onChange={(e) => setRule({ ...rule, name: e.target.value })} />
          <select value={rule.minSeverity} onChange={(e) => setRule({ ...rule, minSeverity: e.target.value })} aria-label="Minimum severity">
            {['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((s) => <option key={s}>{s}</option>)}
          </select>
          <select value={rule.ruleType} onChange={(e) => setRule({ ...rule, ruleType: e.target.value })} aria-label="Detection rule">
            <option value="">Any detection rule</option>{ruleTypes.map((t) => <option key={t}>{t}</option>)}
          </select>
          <label><input type="checkbox" checked={rule.onIncidentCreated} onChange={(e) => setRule({ ...rule, onIncidentCreated: e.target.checked })} /> created</label>
          <label><input type="checkbox" checked={rule.onEscalation} onChange={(e) => setRule({ ...rule, onEscalation: e.target.checked })} /> escalated</label>
          {channels.map((c) => (
            <label key={c.id} className="chip"><input type="checkbox" checked={rule.channelIds.includes(c.id)}
              onChange={(e) => setRule({ ...rule, channelIds: e.target.checked ? [...rule.channelIds, c.id] : rule.channelIds.filter((x) => x !== c.id) })} /> {c.name}</label>
          ))}
          <button type="submit" disabled={rule.channelIds.length === 0}>Add rule</button>
        </form>
      </section>

      <section className="panel">
        <h3>Delivery log</h3>
        {deliveries.length === 0 ? <p className="muted small">No deliveries yet.</p> : (
          <table className="data-table">
            <thead><tr><th>When</th><th>Channel</th><th>Subject</th><th>Status</th><th>Attempts</th><th>Error</th></tr></thead>
            <tbody>
              {deliveries.map((d) => (
                <tr key={d.id}>
                  <td className="small">{new Date(d.createdAt).toLocaleString()}</td><td>{name(d.channelId)}{d.test && <span className="chip">test</span>}</td>
                  <td className="small">{d.subject}</td><td><span className={`ai-badge ${STATUS_CLASS[d.status]}`}>{d.status}</span></td>
                  <td>{d.attempts}</td><td className="small error-text">{d.lastError || ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}
