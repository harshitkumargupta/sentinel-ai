/** Summary of a parse + ingest run (upload, agent batch or replay). */
export default function ParseReport({ report }) {
  if (!report) return null;
  return (
    <div className="panel" style={{ marginTop: '0.75rem' }}>
      <div className="chip-row">
        <span className="chip">{report.format}</span>
        <span className="chip">{report.lines} lines</span>
        <span className="ai-badge badge-valid">{report.accepted} accepted</span>
        {report.duplicates > 0 && <span className="chip">{report.duplicates} duplicates</span>}
        <span className="chip">{report.skipped} skipped (not security-relevant)</span>
        <span className={`ai-badge ${report.parseErrors + report.rejected > 0 ? 'badge-rejected' : 'chip'}`}>
          {report.parseErrors} parse errors{report.rejected > 0 ? `, ${report.rejected} rejected` : ''}
        </span>
      </div>
      {report.errorSamples?.length > 0 && (
        <ul className="muted small">
          {report.errorSamples.map((e) => <li key={e}>{e}</li>)}
        </ul>
      )}
    </div>
  );
}
