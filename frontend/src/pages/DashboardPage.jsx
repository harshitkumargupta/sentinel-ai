import { useEffect, useState } from 'react';
import NavBar from '../components/NavBar.jsx';
import { getSummary } from '../services/dashboard.service.js';

function sum(map) {
  return map ? Object.values(map).reduce((a, b) => a + b, 0) : 0;
}

export default function DashboardPage() {
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    getSummary()
      .then(setSummary)
      .catch(() => setError('Could not load dashboard summary.'));
  }, []);

  const tiles = summary
    ? [
        { label: 'Events (24h)', value: summary.eventsLast24h },
        { label: 'Total events', value: sum(summary.eventsByType) },
        { label: 'Open incidents', value: summary.incidentsByStatus?.OPEN ?? 0 },
        { label: 'Critical events', value: summary.eventsBySeverity?.CRITICAL ?? 0 },
      ]
    : [];

  return (
    <div className="app-shell">
      <NavBar />
      <main className="content">
        <h2>SOC Overview</h2>
        <p className="subtitle">Live counts from the SentinelAI API.</p>

        {error && <p className="error-text">{error}</p>}

        <section className="tiles">
          {tiles.map((t) => (
            <div className="tile" key={t.label}>
              <span className="tile-value">{t.value}</span>
              <span className="tile-label">{t.label}</span>
            </div>
          ))}
        </section>

        {summary && (
          <div className="panel-grid">
            <Breakdown title="Events by severity" data={summary.eventsBySeverity} />
            <Breakdown title="Events by type" data={summary.eventsByType} />
            <Breakdown title="Incidents by status" data={summary.incidentsByStatus} />
            <Breakdown title="Incidents by severity" data={summary.incidentsBySeverity} />
          </div>
        )}
      </main>
    </div>
  );
}

function Breakdown({ title, data }) {
  const entries = Object.entries(data || {});
  return (
    <section className="panel">
      <h3>{title}</h3>
      <ul className="breakdown">
        {entries.map(([k, v]) => (
          <li key={k}>
            <span>{k}</span>
            <span className="count">{v}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
