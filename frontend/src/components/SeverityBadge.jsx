const COLORS = {
  LOW: '#57606a',
  MEDIUM: '#9a6700',
  HIGH: '#bc4c00',
  CRITICAL: '#cf222e',
};

export default function SeverityBadge({ severity }) {
  return (
    <span className="status-badge" style={{ backgroundColor: COLORS[severity] || '#57606a' }}>
      {severity}
    </span>
  );
}
