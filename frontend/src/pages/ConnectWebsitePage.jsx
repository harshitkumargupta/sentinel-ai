import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api.js';
import { useAuth } from '../context/AuthContext.jsx';
import { messageFromError } from '../services/errors.js';
import * as S from './connectSnippets.js';

const METHODS = [
  { id: 'AGENT', label: 'Agent on my server', help: 'A small Python script tails your web server access log and ships new lines.' },
  { id: 'LOG_UPLOAD', label: 'Log file upload', help: 'Upload an access log file from time to time — no install.' },
  { id: 'MIDDLEWARE', label: 'App middleware snippet', help: 'A few lines in your app send every request (time, IP, method, path, status, user agent).' },
  { id: 'MONITOR', label: 'Website monitor', help: 'SentinelAI checks that your URL answers (one plain GET when you click Check). No logs needed.' },
];

function Copy({ text }) {
  const [done, setDone] = useState(false);
  return (
    <div style={{ position: 'relative' }}>
      <button className="ghost" style={{ position: 'absolute', right: 6, top: 6 }}
        onClick={() => { navigator.clipboard?.writeText(text).then(() => { setDone(true); setTimeout(() => setDone(false), 1500); }).catch(() => {}); }}>
        {done ? 'Copied ✓' : 'Copy'}</button>
      <pre className="code-block" style={{ whiteSpace: 'pre-wrap', paddingRight: 80 }}>{text}</pre>
    </div>
  );
}

/** Connect My Website: register an authorized site, get the key once, install, test the connection. */
export default function ConnectWebsitePage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const [form, setForm] = useState({ name: '', url: '', authorized: false, method: 'MIDDLEWARE' });
  const [base, setBase] = useState(window.location.origin);
  const [created, setCreated] = useState(null);
  const [tab, setTab] = useState('node');
  const [conn, setConn] = useState(null);
  const [waiting, setWaiting] = useState(false);
  const [monitor, setMonitor] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const poll = useRef(null);
  useEffect(() => () => clearInterval(poll.current), []);

  async function submit(e) {
    e.preventDefault();
    setBusy(true); setError(null);
    try {
      setCreated((await api.post('/sites/connect', form)).data.data);
      setTab(form.method === 'MIDDLEWARE' ? 'node' : form.method === 'AGENT' ? 'agent' : form.method === 'LOG_UPLOAD' ? 'upload' : 'monitor');
    } catch (err) { setError(messageFromError(err)); }
    finally { setBusy(false); }
  }

  async function check() {
    const c = (await api.get(`/sites/${created.site.id}/connection`)).data.data;
    setConn(c);
    return c;
  }

  async function testConnection() {
    setError(null); setWaiting(true);
    clearInterval(poll.current);
    const started = Date.now();
    try {
      if ((await check()).connected) { setWaiting(false); return; }
      poll.current = setInterval(async () => {
        try {
          const c = await check();
          if (c.connected || Date.now() - started > 120000) { clearInterval(poll.current); setWaiting(false); }
        } catch (err) { clearInterval(poll.current); setWaiting(false); setError(messageFromError(err)); }
      }, 3000);
    } catch (err) { setWaiting(false); setError(messageFromError(err)); }
  }

  async function runMonitor() {
    try { setMonitor((await api.post(`/sites/${created.site.id}/monitor-check`)).data.data); check(); }
    catch (err) { setError(messageFromError(err)); }
  }

  if (!isAdmin) {
    return (<><h2>Connect My Website</h2><p className="muted">Only an administrator can connect a website. Ask an admin, or see <Link to="/sites">Sites</Link>.</p></>);
  }
  const key = created?.apiKey.apiKey;
  const tabs = { agent: 'Agent', upload: 'Log upload', node: 'Node / Express', spring: 'Spring Boot', flask: 'Python / Flask', django: 'Django', monitor: 'Website monitor' };

  return (
    <>
      <h2>Connect My Website</h2>
      <p className="subtitle">Send your own website's traffic to SentinelAI so the SOC pages show what happens on it. Only connect sites you own or are authorized to monitor.</p>
      {error && <p className="error-text">{error}</p>}

      <section className="panel">
        <h3>1. Your site</h3>
        <form onSubmit={submit} style={{ display: 'grid', gap: 10, maxWidth: 640 }}>
          <label>Site name <input required maxLength={150} value={form.name} disabled={!!created} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="My Shop" /></label>
          <label>Site URL <input required maxLength={255} type="url" value={form.url} disabled={!!created} onChange={(e) => setForm({ ...form, url: e.target.value })} placeholder="https://shop.example.com" /></label>
          <fieldset disabled={!!created} style={{ border: 0, padding: 0 }}>
            <legend className="small muted">2. How will data arrive?</legend>
            {METHODS.map((m) => (
              <label key={m.id} style={{ display: 'block', margin: '4px 0' }}>
                <input type="radio" name="method" checked={form.method === m.id} onChange={() => setForm({ ...form, method: m.id })} /> <strong>{m.label}</strong>
                <span className="muted small"> — {m.help}</span>
              </label>
            ))}
          </fieldset>
          <label><input type="checkbox" checked={form.authorized} disabled={!!created} onChange={(e) => setForm({ ...form, authorized: e.target.checked })} /> I own or am authorized to test this site</label>
          {!created && <button type="submit" disabled={busy || !form.authorized}>{busy ? 'Connecting…' : 'Connect and create API key'}</button>}
        </form>
      </section>

      {created && (
        <>
          <section className="panel">
            <h3>3. Your API key</h3>
            <p className="error-text">Copy it now — it is shown only once. SentinelAI stores only its hash. Lost it? Rotate it on the <Link to="/sites">Sites</Link> page.</p>
            <Copy text={key} />
            <label className="small">SentinelAI URL your server can reach{' '}
              <input value={base} onChange={(e) => setBase(e.target.value.replace(/\/$/, ''))} style={{ minWidth: 280 }} /></label>
            <p className="muted small">If your site runs on another machine, replace localhost with this computer's LAN address (e.g. http://192.168.1.20:8088).</p>
          </section>

          <section className="panel">
            <h3>4. Install</h3>
            <div className="chip-row">{Object.entries(tabs).map(([k, v]) => (
              <button key={k} className={tab === k ? '' : 'ghost'} onClick={() => setTab(k)}>{v}</button>))}</div>
            {tab === 'agent' && <Copy text={S.agentCommand(base, key)} />}
            {tab === 'upload' && <><Copy text={S.uploadSteps()} /><Link to="/log-sources">Open Log Sources → Upload</Link></>}
            {tab === 'node' && <Copy text={S.nodeSnippet(base, key)} />}
            {tab === 'spring' && <Copy text={S.springSnippet(base, key)} />}
            {tab === 'flask' && <Copy text={S.flaskSnippet(base, key)} />}
            {tab === 'django' && <Copy text={S.djangoSnippet(base, key)} />}
            {tab === 'monitor' && (
              <div>
                <p className="small">Sends one plain GET to <code>{created.site.domain}</code> (5 s timeout, no redirects) and records up/down and latency as an event.</p>
                <button onClick={runMonitor}>Check now</button>
                {monitor && <p className="small">{monitor.reachable ? `✓ Up — HTTP ${monitor.status}` : `✗ Unreachable${monitor.status ? ` — HTTP ${monitor.status}` : ''}${monitor.error ? ` (${monitor.error})` : ''}`} · {monitor.latencyMs} ms</p>}
              </div>
            )}
            <h4>Quick test from any terminal</h4>
            <Copy text={S.curlTest(base, key)} />
          </section>

          <section className="panel">
            <h3>5. Test connection</h3>
            <button onClick={testConnection} disabled={waiting}>{waiting ? 'Waiting for the first event…' : 'Test connection'}</button>
            {conn && (conn.connected ? (
              <p><span className="ai-badge badge-valid">Connected</span> {conn.eventCount} event(s) · last received {new Date(conn.lastEventAt).toLocaleString()}
                {conn.lastEvent && <span className="muted small"> — {conn.lastEvent.type} {conn.lastEvent.sourceIp || ''} {conn.lastEvent.resource || ''}</span>}</p>
            ) : !waiting && <p className="muted">No events yet. Run the curl test above, then try again.</p>)}
          </section>

          <section className="panel">
            <h3>6. Generate test traffic against my site</h3>
            <p className="small">Run these <strong>yourself</strong>, only against a site you own. SentinelAI never sends these requests. Then watch <Link to="/offenses">Offenses</Link>, <Link to="/alerts">Alerts</Link> and <Link to="/search">Event Search</Link>.</p>
            {S.testTraffic(form.url).map((t) => (
              <div key={t.title}><h4>{t.title}</h4><Copy text={t.cmd} />{t.note && <p className="muted small">{t.note}</p>}</div>
            ))}
            <button className="ghost" onClick={() => { setCreated(null); setConn(null); setMonitor(null); setForm({ name: '', url: '', authorized: false, method: 'MIDDLEWARE' }); }}>Connect another site</button>
          </section>
        </>
      )}
    </>
  );
}
