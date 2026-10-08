import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api.js';
import { messageFromError } from '../services/errors.js';
import { useLiveRefresh } from '../hooks/useLiveRefresh.js';

const SEV = [['critical', '#ff5a5f'], ['high', '#ff8c42'], ['medium', '#f0b429'], ['low', '#3ddc97']];

function SeverityChart({ days }) {
  const max = Math.max(1, ...days.map((d) => d.low + d.medium + d.high + d.critical));
  const w = Math.max(8, Math.floor(600 / Math.max(1, days.length)) - 2);
  return (
    <svg width="100%" viewBox={`0 0 ${days.length * (w + 2)} 120`} role="img" aria-label="Incidents by severity per day">
      {days.map((d, i) => {
        let y = 120;
        return SEV.map(([k, c]) => {
          const h = (d[k] / max) * 110;
          y -= h;
          return h > 0 ? <rect key={d.day + k} x={i * (w + 2)} y={y} width={w} height={h} fill={c}><title>{`${d.day} ${k}: ${d[k]}`}</title></rect> : null;
        });
      })}
    </svg>
  );
}

/** Executive view: posture score (with formula), MTTD/MTTR, severity trend, top risks, plain-language summary. */
export default function ExecutivePage() {
  const [days, setDays] = useState(30);
  const [s, setS] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try { setS((await api.get('/executive', { params: { days } })).data.data); setError(null); }
    catch (e) { setError(messageFromError(e)); }
    finally { setLoading(false); }
  }, [days]);
  useEffect(() => { load(); }, [load]);
  useLiveRefresh(load, 0);

  async function exportPdf() {
    try {
      const res = await api.get('/executive/report.pdf', { params: { days }, responseType: 'blob' });
      const url = URL.createObjectURL(res.data);
      const a = document.createElement('a');
      a.href = url; a.download = 'sentinel-executive-summary.pdf';
      document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url);
    } catch (e) { setError(messageFromError(e)); }
  }

  const color = !s ? 'inherit' : s.postureScore >= 85 ? '#3ddc97' : s.postureScore >= 70 ? '#f0b429' : s.postureScore >= 50 ? '#ff8c42' : '#ff5a5f';
  return (
    <>
      <h2>Executive Summary</h2>
      <div className="filters">
        <select value={days} onChange={(e) => setDays(Number(e.target.value))} aria-label="Period">
          <option value={7}>Last 7 days</option><option value={30}>Last 30 days</option><option value={90}>Last 90 days</option>
        </select>
        <button className="ghost" onClick={exportPdf} disabled={!s}>Export PDF</button>
      </div>
      {error && <p className="error-text">{error} <button className="ghost" onClick={load}>Retry</button></p>}
      {loading && !s ? <p className="muted">Loading…</p> : s && (
        <>
          <div className="panel-grid">
            <section className="panel">
              <div className="muted small">Security posture</div>
              <div style={{ fontSize: '3rem', fontWeight: 700, color }}>{s.postureScore}<span className="muted small">/100</span></div>
              <div><strong>{s.grade}</strong></div>
              <code className="small">{s.formula}</code>
              <ul className="breakdown">{s.penalties.map((p) => (
                <li key={p.factor}><span>{p.factor}</span><span className="muted small">−{p.points} · {p.detail}</span></li>))}</ul>
            </section>
            <section className="panel">
              <div className="kpi-row">
                <div><div className="muted small">MTTD</div><strong>{s.mttdMinutes != null ? `${s.mttdMinutes} min` : '—'}</strong></div>
                <div><div className="muted small">MTTR</div><strong>{s.mttrHours != null ? `${s.mttrHours} h` : '—'}</strong></div>
                <div><div className="muted small">Open</div><strong>{s.open}</strong></div>
                <div><div className="muted small">Resolved</div><strong>{s.resolved}</strong></div>
                <div><div className="muted small">Coverage</div><strong>{s.coveragePct != null ? `${s.coveragePct}%` : <Link to="/coverage">run test</Link>}</strong></div>
              </div>
              {s.incidents > 0 && (
                <div style={{ display: 'flex', height: 10, borderRadius: 5, overflow: 'hidden', marginTop: 8 }} title={`${s.open} open / ${s.resolved} resolved`}>
                  <div style={{ flex: s.open, background: '#ff8c42' }} /><div style={{ flex: s.resolved, background: '#3ddc97' }} />
                </div>
              )}
              <h4>In plain language</h4>
              <ul>{s.plainLanguage.map((l, i) => <li key={i}>{l}</li>)}</ul>
            </section>
          </div>
          <section className="panel">
            <h3>Incidents by severity over time</h3>
            {s.incidents === 0 ? <p className="muted">No incidents in this period.</p> : <SeverityChart days={s.bySeverity} />}
            <div className="chip-row">{SEV.map(([k, c]) => <span key={k} className="chip" style={{ borderColor: c }}>{k}</span>)}</div>
          </section>
          <section className="panel">
            <h3>Top open risks</h3>
            {s.topRisks.length === 0 ? <p className="muted">No open incidents.</p> : (
              <table className="data-table">
                <thead><tr><th>Incident</th><th>Severity</th><th>Risk</th><th>Status</th><th>Owner</th></tr></thead>
                <tbody>{s.topRisks.map((r) => (
                  <tr key={r.incidentId}><td><Link to={`/incidents/${r.incidentId}`}>#{r.incidentId} {r.title}</Link></td>
                    <td>{r.severity}</td><td>{r.riskScore ?? '—'}</td><td>{r.status}</td><td>{r.assignee || <span className="muted">unassigned</span>}</td></tr>))}
                </tbody>
              </table>
            )}
          </section>
        </>
      )}
    </>
  );
}
