import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getSimilar } from '../services/incidents.service.js';
import { messageFromError } from '../services/errors.js';

/** Similar past incidents with how they were resolved and a "what worked before" hint. */
export default function SimilarIncidentsPanel({ incidentId }) {
  const [items, setItems] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let active = true;
    getSimilar(incidentId, 5)
      .then((d) => { if (active) setItems(d); })
      .catch((e) => { if (active) setError(messageFromError(e)); });
    return () => { active = false; };
  }, [incidentId]);

  const hint = (items || []).find((i) => i.actionResolved);

  return (
    <section className="panel">
      <h3>Similar past incidents</h3>
      {error && <p className="error-text">{error}</p>}
      {!items && !error && <div className="state-box">Loading…</div>}
      {items && items.length === 0 && <p className="muted">No similar incidents found.</p>}

      {hint && hint.actionsTaken.length > 0 && (
        <p className="ai-badge badge-valid" style={{ display: 'inline-block' }}>
          What worked before: {hint.actionsTaken[0]} (incident #{hint.incidentId})
        </p>
      )}

      {items && items.length > 0 && (
        <table className="data-table">
          <thead>
            <tr><th>Incident</th><th>Score</th><th>Shared</th><th>Resolution</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {items.map((i) => (
              <tr key={i.incidentId}>
                <td><Link to={`/incidents/${i.incidentId}`}>#{i.incidentId}</Link></td>
                <td>{i.score.toFixed(2)}</td>
                <td className="muted small">{i.sharedFeatures.slice(0, 4).join(', ')}</td>
                <td className="small">{i.status} / {i.feedback}</td>
                <td className="muted small">{i.actionsTaken.join(', ') || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
