/** Compact 0–10 magnitude bar (QRadar style), colored by band. */
export default function MagnitudeBar({ value }) {
  const v = Math.max(0, Math.min(10, value ?? 0));
  const color = v >= 8 ? 'var(--sev-critical, #ff5a5f)' : v >= 6 ? 'var(--sev-high, #ff8c42)'
    : v >= 4 ? 'var(--sev-medium, #f0b429)' : 'var(--sev-low, #3ddc97)';
  return (
    <span className="magnitude" title={`Magnitude ${v}/10`} aria-label={`Magnitude ${v} of 10`}>
      <span className="risk-bar-wrap" style={{ width: 70 }}>
        <span className="risk-bar" style={{ width: `${v * 10}%`, background: color }} />
      </span>
      <strong className="small"> {v}</strong>
    </span>
  );
}
