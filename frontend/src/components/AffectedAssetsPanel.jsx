import { Link } from 'react-router-dom';
import SeverityBadge from './SeverityBadge.jsx';

/** Inventoried assets this incident touches, with their open vulnerabilities. */
export default function AffectedAssetsPanel({ assets }) {
  if (!assets?.length) return null;
  return (
    <section className="panel">
      <h3>Affected assets</h3>
      <table className="data-table">
        <thead><tr><th>Asset</th><th>Criticality</th><th>Type / environment</th><th>Owner</th><th>Open vulnerabilities</th></tr></thead>
        <tbody>
          {assets.map((a) => (
            <tr key={a.id}>
              <td><Link to="/assets"><strong>{a.label}</strong></Link></td>
              <td><SeverityBadge severity={a.criticality} /></td>
              <td>{a.type} · {a.environment}</td>
              <td>{a.owner || '—'}</td>
              <td>{a.vulnerabilities.length === 0 ? <span className="muted">none</span> : a.vulnerabilities.map((v) => (
                <div key={v.id} className="small"><SeverityBadge severity={v.severity} /> <code>{v.cveId}</code> {v.description}</div>
              ))}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
