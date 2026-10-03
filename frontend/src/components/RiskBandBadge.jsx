const COLORS = { LOW: '#57606a', MEDIUM: '#9a6700', HIGH: '#bc4c00', CRITICAL: '#cf222e' };

export default function RiskBandBadge({ band }) {
  return (
    <span className="status-badge" style={{ backgroundColor: COLORS[band] || '#57606a' }}>
      {band}
    </span>
  );
}
