import MagnitudeBar from './MagnitudeBar.jsx';

const PARTS = [
  { key: 'severity', label: 'Severity', why: 'severityReasons', hint: 'How bad the activity is (risk score / 10)' },
  { key: 'relevance', label: 'Relevance', why: 'relevanceReasons', hint: 'How much the target matters' },
  { key: 'credibility', label: 'Credibility', why: 'credibilityReasons', hint: 'How trustworthy the evidence is' },
];

/** Offense magnitude with its three components, their reasons and the exact formula used. */
export default function MagnitudePanel({ magnitude }) {
  if (!magnitude) return null;
  return (
    <section className="panel">
      <div className="brand-row" style={{ justifyContent: 'space-between' }}>
        <h3>Magnitude</h3>
        <MagnitudeBar value={magnitude.magnitude} />
      </div>
      <div className="panel-grid">
        {PARTS.map((p) => (
          <div key={p.key}>
            <div className="muted small" title={p.hint}>{p.label}</div>
            <MagnitudeBar value={magnitude[p.key]} />
            <ul className="muted small">
              {(magnitude[p.why] || []).map((r) => <li key={r}>{r}</li>)}
            </ul>
          </div>
        ))}
      </div>
      <p className="small">Formula (weights severity 3, relevance 2, credibility 1): <code>{magnitude.formula}</code></p>
    </section>
  );
}
