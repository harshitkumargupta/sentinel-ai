import { useCallback, useEffect, useState } from 'react';
import DataState from '../components/DataState.jsx';
import ParseReport from '../components/ParseReport.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { emitDataChanged, useLiveRefresh } from '../hooks/useLiveRefresh.js';
import {
  SOURCE_TYPES, LOG_FORMATS, listLogSources, createLogSource, setLogSourceEnabled, rotateLogSourceKey,
  deleteLogSource, uploadLogFile,
} from '../services/logsources.service.js';
import { getDemoInfo, listDatasets, replayDataset } from '../services/demo.service.js';
import { messageFromError } from '../services/errors.js';

const HEALTH_STYLE = {
  RECEIVING: { background: '#1a7f37', label: 'receiving' },
  IDLE: { background: '#9a6700', label: 'idle' },
  NEVER: { background: '#57606a', label: 'no events yet' },
  DISABLED: { background: '#8250df', label: 'disabled' },
};

function ingestSnippet(key) {
  const origin = typeof window !== 'undefined' ? window.location.origin : 'http://localhost';
  return `curl -X POST ${origin}/api/ingest/events \\
  -H 'X-API-Key: ${key}' -H 'Content-Type: application/json' \\
  -d '{"eventType":"FAILED_LOGIN","severity":"LOW","username":"jdoe","sourceIp":"203.0.113.5"}'`;
}

/** QRadar-style Log Sources: onboard a source (type + one-time key), watch health/EPS, manage keys. */
export default function LogSourcesPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const [sources, setSources] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [form, setForm] = useState({ name: '', type: 'WEB_SERVER', description: '' });
  const [newKey, setNewKey] = useState(null); // { name, apiKey } — shown once
  const [upload, setUpload] = useState({ sourceId: '', format: '', file: null });
  const [uploading, setUploading] = useState(false);
  const [uploadReport, setUploadReport] = useState(null);
  const [datasets, setDatasets] = useState([]);
  const [replaying, setReplaying] = useState(null);
  const [replayResult, setReplayResult] = useState(null);

  const load = useCallback(async () => {
    try { setSources(await listLogSources()); setError(null); } catch (e) { setError(messageFromError(e)); }
    finally { setLoading(false); }
  }, []);
  useLiveRefresh(load, 5000);

  // Sample replay is a Demo Center feature: only offered when the server runs in demo mode.
  useEffect(() => {
    if (!isAdmin) return;
    getDemoInfo()
      .then((info) => (info.demoMode ? listDatasets().then(setDatasets) : null))
      .catch(() => setDatasets([]));
  }, [isAdmin]);

  async function handleUpload(e) {
    e.preventDefault();
    if (!upload.file || !upload.sourceId) return;
    setUploading(true);
    setUploadReport(null);
    try {
      setUploadReport(await uploadLogFile(upload.sourceId, upload.file, upload.format));
      emitDataChanged('upload');
      await load();
    } catch (err) {
      setError(messageFromError(err));
    } finally {
      setUploading(false);
    }
  }

  async function handleReplay(id) {
    setReplaying(id);
    setReplayResult(null);
    try {
      setReplayResult(await replayDataset(id));
      emitDataChanged('replay');
      await load();
    } catch (err) {
      setError(messageFromError(err));
    } finally {
      setReplaying(null);
    }
  }

  async function act(fn) {
    try { await fn(); await load(); } catch (e) { setError(messageFromError(e)); }
  }

  async function handleCreate(e) {
    e.preventDefault();
    await act(async () => {
      const res = await createLogSource(form);
      setNewKey({ name: res.source.name, apiKey: res.apiKey.apiKey });
      setForm({ name: '', type: form.type, description: '' });
    });
  }

  async function handleDelete(s) {
    if (!window.confirm(`Delete log source "${s.name}"? Its keys stop working; past events are kept.`)) return;
    await act(() => deleteLogSource(s.id));
  }

  return (
    <>
      <h2>Log Sources</h2>
      <p className="subtitle">Systems that send events to SentinelAI. Each source has its own ingest key.</p>
      {error && <p className="error-text">{error}</p>}

      {isAdmin && (
        <section className="panel">
          <h3>Add a log source</h3>
          <form className="filters" onSubmit={handleCreate}>
            <input placeholder="Name (e.g. web-01 nginx)" value={form.name} maxLength={150} required
              onChange={(e) => setForm({ ...form, name: e.target.value })} />
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })} aria-label="Source type">
              {SOURCE_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
            </select>
            <input placeholder="Description (optional)" value={form.description} maxLength={500}
              onChange={(e) => setForm({ ...form, description: e.target.value })} />
            <button type="submit">Create + issue key</button>
          </form>
          {newKey && (
            <div className="panel" style={{ marginTop: '1rem' }}>
              <p className="error-text">Copy the key for “{newKey.name}” now — it is shown only once (only a hash is stored):</p>
              <pre className="code-block">{newKey.apiKey}</pre>
              <h4>Send a test event</h4>
              <pre className="code-block">{ingestSnippet(newKey.apiKey)}</pre>
              <button className="ghost" onClick={() => setNewKey(null)}>I saved it — hide</button>
            </div>
          )}
        </section>
      )}

      <section className="panel">
        <h3>Upload a log file</h3>
        <p className="muted small">Parsed by format, normalized and run through detection. Max 1 MB per file.</p>
        <form className="filters" onSubmit={handleUpload}>
          <select value={upload.sourceId} required aria-label="Log source"
            onChange={(e) => setUpload({ ...upload, sourceId: e.target.value })}>
            <option value="">Choose a log source…</option>
            {(sources ?? []).filter((s) => s.enabled).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
          <select value={upload.format} aria-label="Format" onChange={(e) => setUpload({ ...upload, format: e.target.value })}>
            {LOG_FORMATS.map((f) => <option key={f.value} value={f.value}>{f.label}</option>)}
          </select>
          <input type="file" accept=".log,.txt,.json,.jsonl,.csv" required aria-label="Log file"
            onChange={(e) => setUpload({ ...upload, file: e.target.files?.[0] ?? null })} />
          <button type="submit" disabled={uploading}>{uploading ? 'Parsing…' : 'Upload & ingest'}</button>
        </form>
        <ParseReport report={uploadReport} />
      </section>

      {datasets.length > 0 && (
        <section className="panel">
          <h3>Replay a sample dataset <span className="chip">demo</span></h3>
          <p className="muted small">Streams a bundled log file through the parsers and pipeline, re-timed to now.
            Removed by Demo Center → Reset Demo Data.</p>
          <table className="data-table">
            <thead><tr><th>Dataset</th><th>Format</th><th>Expected detections</th><th></th></tr></thead>
            <tbody>
              {datasets.map((d) => (
                <tr key={d.id}>
                  <td><strong>{d.label}</strong><div className="muted small">{d.description}</div></td>
                  <td>{d.format}</td>
                  <td>{d.expectedRules.length ? d.expectedRules.join(', ') : '—'}</td>
                  <td><button disabled={replaying !== null} onClick={() => handleReplay(d.id)}>
                    {replaying === d.id ? 'Replaying…' : 'Replay'}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
          {replayResult && (
            <>
              <p>Replayed into <strong>{replayResult.sourceName}</strong>: {replayResult.alertsCreated} alert(s)
                {replayResult.rulesFired.length > 0 && <> from {replayResult.rulesFired.join(', ')}</>},
                {' '}{replayResult.incidentIds.length} incident(s).</p>
              <ParseReport report={replayResult.report} />
            </>
          )}
        </section>
      )}

      <DataState loading={loading} error={!sources && error} empty={(sources ?? []).length === 0} emptyText="No log sources yet.">
        <table className="data-table">
          <thead>
            <tr><th>Name</th><th>Type</th><th>Status</th><th>Last event</th><th>EPS</th><th>Total events</th>
              <th>Parse errors</th><th>Keys</th>{isAdmin && <th></th>}</tr>
          </thead>
          <tbody>
            {(sources ?? []).map((s) => {
              const h = HEALTH_STYLE[s.health] || HEALTH_STYLE.NEVER;
              return (
                <tr key={s.id}>
                  <td><strong>{s.name}</strong>{s.description && <div className="muted small">{s.description}</div>}</td>
                  <td>{s.type}</td>
                  <td><span className="status-badge" style={{ background: h.background }}>{h.label}</span></td>
                  <td>{s.lastEventAt ? new Date(s.lastEventAt).toLocaleString() : '—'}</td>
                  <td>{s.eventsPerSecond.toFixed(2)}</td>
                  <td>{s.totalEvents.toLocaleString()}</td>
                  <td className={s.parseErrors > 0 ? 'error-text' : ''}>{s.parseErrors}</td>
                  <td>{s.activeKeys}</td>
                  {isAdmin && (
                    <td>
                      <button className="ghost" onClick={() => act(() => setLogSourceEnabled(s.id, !s.enabled))}>
                        {s.enabled ? 'Disable' : 'Enable'}
                      </button>
                      <button className="ghost" onClick={() => act(async () => {
                        const k = await rotateLogSourceKey(s.id);
                        setNewKey({ name: s.name, apiKey: k.apiKey });
                      })}>Rotate key</button>
                      {s.id !== 1 && <button className="ghost" onClick={() => handleDelete(s)}>Delete</button>}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </DataState>
    </>
  );
}
