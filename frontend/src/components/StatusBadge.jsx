const COLORS = {
  up: '#1a7f37',
  down: '#cf222e',
  checking: '#9a6700',
};

const LABELS = {
  up: 'Backend online',
  down: 'Backend unreachable',
  checking: 'Checking…',
};

export default function StatusBadge({ status }) {
  return (
    <span className="status-badge" style={{ backgroundColor: COLORS[status] || '#57606a' }}>
      <span className="status-dot" />
      {LABELS[status] || status}
    </span>
  );
}
