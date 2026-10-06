/**
 * Horizontal risk breakdown: one bar per factor (width ∝ points), its reason, and the total.
 */
export default function RiskWaterfall({ risk }) {
  if (!risk) return null;
  const max = Math.max(risk.score, 1);
  const contributing = (risk.breakdown || []).filter((f) => f.points > 0);

  return (
    <div className="waterfall">
      <div className="waterfall-total">
        Risk <strong>{risk.score}</strong> / 100 · <span className="role-chip">{risk.severity}</span>
      </div>
      {contributing.length === 0 && <p className="muted">No contributing factors.</p>}
      {contributing.map((f) => (
        <div className="wf-row" key={f.name}>
          <span className="wf-label">{f.name}</span>
          <span className="wf-bar-wrap">
            <span className="wf-bar" style={{ width: `${(f.points / max) * 100}%` }} />
          </span>
          <span className="wf-points">+{f.points}</span>
          <span className="wf-reason muted">{f.reason}</span>
        </div>
      ))}
    </div>
  );
}
