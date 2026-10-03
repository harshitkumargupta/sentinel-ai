export default function MitreChip({ technique }) {
  if (!technique) {
    return <span className="muted">—</span>;
  }
  return (
    <a
      className="mitre-chip"
      href={`https://attack.mitre.org/techniques/${technique.replace('.', '/')}/`}
      target="_blank"
      rel="noreferrer"
    >
      {technique}
    </a>
  );
}
