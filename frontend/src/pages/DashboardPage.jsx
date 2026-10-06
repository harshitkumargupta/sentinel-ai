import { useEffect, useState } from 'react';
import NavBar from '../components/NavBar.jsx';
import TuningCard from '../components/TuningCard.jsx';
import { getSummary, getAlertReduction, getMitreCoverage } from '../services/dashboard.service.js';
import { messageFromError } from '../services/errors.js';

export default function DashboardPage() {
  const [summary, setSummary] = useState(null);
  const [reduction, setReduction] = useState(null);
  const [mitre, setMitre] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    Promise.all([getSummary(), getAlertReduction(), getMitreCoverage()])
      .then(([s, r, m]) => { setSummary(s); setReduction(r); setMitre(m); })
      .catch((e) => setError(messageFromError(e)));
  }, []);

  const maxHits = Math.max(1, ...mitre.map((m) => m.alertCount));
  const heat = (n) => {
    if (n === 0) return '#161b22';
    const t = Math.min(1, n / maxHits);
    return `rgba(207, 34, 46, ${0.2 + t * 0.8})`;
  };

  return (
    <div className="app-shell">
      <NavBar />
      <main className="content">
        <h2>SOC Overview</h2>
        {error && <p className="error-text">{error}</p>}

        {reduction && (
          <section className="panel reduction-card">
            <h3>Alert reduction</h3>
            <p className="reduction-line">
              <strong>{reduction.events.toLocaleString()}</strong> events →{' '}
              <strong>{reduction.alerts.toLocaleString()}</strong> alerts →{' '}
              <strong>{reduction.incidents.toLocaleString()}</strong> incidents
            </p>
            <p className="subtitle">{reduction.reductionPct}% fewer things to look at than raw events.</p>
          </section>
        )}

        <TuningCard />

        {summary && (
          <section className="tiles">
            <div className="tile"><span className="tile-value">{summary.eventsLast24h}</span><span className="tile-label">Events (24h)</span></div>
            <div className="tile"><span className="tile-value">{summary.incidentsByStatus?.OPEN ?? 0}</span><span className="tile-label">Open incidents</span></div>
            <div className="tile"><span className="tile-value">{summary.eventsBySeverity?.CRITICAL ?? 0}</span><span className="tile-label">Critical events</span></div>
            <div className="tile"><span className="tile-value">{summary.incidentsBySeverity?.HIGH ?? 0}</span><span className="tile-label">High incidents</span></div>
          </section>
        )}

        <section className="panel">
          <h3>MITRE ATT&CK coverage</h3>
          {mitre.length === 0 ? (
            <p className="muted">No coverage yet.</p>
          ) : (
            <div className="heatmap">
              {mitre.map((m) => (
                <div className="heat-cell" key={m.technique} style={{ background: heat(m.alertCount) }}
                     title={`${m.ruleCount} rule(s), ${m.alertCount} alert(s)`}>
                  <span className="heat-tech">{m.technique}</span>
                  <span className="heat-count">{m.alertCount}</span>
                </div>
              ))}
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
