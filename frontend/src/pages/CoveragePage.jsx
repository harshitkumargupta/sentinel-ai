import { useCallback, useEffect, useMemo, useState } from 'react';
import api from '../services/api.js';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../components/ui/index.js';
import { emitDataChanged } from '../hooks/useLiveRefresh.js';
import { messageFromError } from '../services/errors.js';

const CELL = {
  DETECTED: { bg: 'rgba(61,220,151,0.25)', border: '#3ddc97', label: 'Detected' },
  MISSED: { bg: 'rgba(255,90,95,0.25)', border: '#ff5a5f', label: 'Missed' },
  UNTESTED: { bg: 'transparent', border: 'var(--border, #555)', label: 'Untested' },
};

async function download(id, format) {
  const res = await api.get(`/coverage/runs/${id}/export`, { params: { format }, responseType: 'blob' });
  const url = URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = url;
  a.download = `sentinel-coverage-${id}.${format}`;
  document.body.appendChild(a); a.click(); a.remove();
  URL.revokeObjectURL(url);
}

/** Detection coverage: run the simulator scenarios, see what was caught, the MITRE matrix and the gaps. */
export default function CoveragePage() {
  const { hasRole } = useAuth();
  const { push } = useToast();
  const [runs, setRuns] = useState([]);
  const [selected, setSelected] = useState(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      const r = (await api.get('/coverage/runs')).data.data;
      setRuns(r); setSelected((s) => s ?? r[0] ?? null); setError(null);
    } catch (e) { setError(messageFromError(e)); }
  }, []);
  useEffect(() => { load(); }, [load]);

  async function run() {
    setRunning(true);
    try {
      const r = (await api.post('/coverage/run')).data.data;
      setSelected(r); push(`Coverage ${r.coveragePct}% (${r.detected}/${r.tested})`, { variant: 'success' });
      emitDataChanged('coverage'); load();
    } catch (e) { setError(messageFromError(e)); }
    finally { setRunning(false); }
  }

  const byTactic = useMemo(() => {
    const m = new Map();
    (selected?.report.matrix ?? []).forEach((c) => { if (!m.has(c.tactic)) m.set(c.tactic, []); m.get(c.tactic).push(c); });
    return [...m.entries()];
  }, [selected]);

  const trend = [...runs].reverse();
  return (
    <>
      <h2>Detection Coverage</h2>
      <p className="subtitle">Runs every existing simulator attack scenario through the real pipeline and checks which ones your
        rules catch, mapped onto MITRE ATT&amp;CK. Generated data is removed by Demo Center → Reset Demo Data.</p>
      {error && <p className="error-text">{error}</p>}
      <div className="filters">
        {hasRole('ADMIN') && <button onClick={run} disabled={running}>{running ? 'Running scenarios…' : 'Run coverage test'}</button>}
        {selected && <>
          <button className="ghost" onClick={() => download(selected.id, 'pdf')}>Export PDF</button>
          <button className="ghost" onClick={() => download(selected.id, 'csv')}>Export CSV</button>
        </>}
        {runs.length > 1 && (
          <select value={selected?.id ?? ''} onChange={(e) => setSelected(runs.find((r) => r.id === Number(e.target.value)))} aria-label="Run">
            {runs.map((r) => <option key={r.id} value={r.id}>{new Date(r.createdAt).toLocaleString()} — {r.coveragePct}%</option>)}
          </select>
        )}
      </div>

      {!selected ? <p className="muted">No coverage runs yet.</p> : (
        <>
          <section className="panel">
            <div className="brand-row" style={{ gap: '2rem' }}>
              <div><div className="muted small">Coverage</div><div style={{ fontSize: '2.2rem', fontWeight: 700 }}>{selected.coveragePct}%</div>
                <div className="muted small">{selected.detected} of {selected.tested} scenarios detected</div></div>
              {trend.length > 1 && (
                <div><div className="muted small">History</div>
                  <svg width={trend.length * 18} height={50} role="img" aria-label="Coverage over time">
                    {trend.map((r, i) => <rect key={r.id} x={i * 18} y={50 - r.coveragePct / 2} width={12} height={r.coveragePct / 2} fill="currentColor" opacity={0.7}><title>{r.coveragePct}%</title></rect>)}
                  </svg></div>
              )}
            </div>
          </section>

          <section className="panel">
            <h3>Scenarios</h3>
            <table className="data-table">
              <thead><tr><th>Scenario</th><th>Expected rule</th><th>MITRE</th><th>Result</th><th>Rules fired</th><th>Time to detect</th></tr></thead>
              <tbody>
                {selected.report.scenarios.map((s) => (
                  <tr key={s.scenario}>
                    <td>{s.label}</td><td><code>{s.expectedRule}</code></td><td>{s.technique || '—'}</td>
                    <td><span className={`ai-badge ${s.detected ? 'badge-valid' : 'badge-rejected'}`}>{s.detected ? 'DETECTED' : 'MISSED'}</span></td>
                    <td className="small">{s.rulesFired.join(', ') || '—'}</td>
                    <td>{s.timeToDetectMs != null ? `${s.timeToDetectMs} ms` : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>

          <section className="panel">
            <h3>MITRE ATT&amp;CK matrix</h3>
            <div className="chip-row">{Object.entries(CELL).map(([k, v]) => (
              <span key={k} className="chip" style={{ background: v.bg, borderColor: v.border }}>{v.label}</span>))}</div>
            <div style={{ display: 'grid', gridTemplateColumns: `repeat(${Math.max(1, byTactic.length)}, minmax(140px, 1fr))`, gap: 8, overflowX: 'auto' }}>
              {byTactic.map(([tactic, cells]) => (
                <div key={tactic}>
                  <div className="muted small" style={{ fontWeight: 600, marginBottom: 4 }}>{tactic}</div>
                  {cells.map((c) => (
                    <div key={c.technique} title={c.detail || CELL[c.status].label}
                      style={{ border: `1px solid ${CELL[c.status].border}`, background: CELL[c.status].bg, borderRadius: 6, padding: 6, marginBottom: 6 }}>
                      <div className="small"><strong>{c.technique}</strong></div>
                      <div className="small">{c.name}</div>
                    </div>
                  ))}
                </div>
              ))}
            </div>
          </section>

          <section className="panel">
            <h3>Gaps &amp; suggestions ({selected.report.gaps.length})</h3>
            <ul className="breakdown">
              {selected.report.gaps.map((g, i) => (
                <li key={i}><span><strong>{g.item}</strong> — {g.reason}</span><span className="muted small">{g.suggestion}</span></li>
              ))}
            </ul>
          </section>
        </>
      )}
    </>
  );
}
