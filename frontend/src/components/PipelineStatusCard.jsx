/**
 * One-glance Kafka pipeline health: outbox backlog, DLQ size, and per-group consumer lag plus
 * listener (running/paused) state. Purely presentational; the parent owns loading/error state.
 */
export default function PipelineStatusCard({ status, onPause, onResume }) {
  if (!status) {
    return null;
  }
  return (
    <div className="pipeline-status">
      <div className="stat-row">
        <div className="stat-tile">
          <span className="stat-label">Outbox backlog</span>
          <span className="stat-value">{status.outboxBacklog}</span>
        </div>
        <div className="stat-tile">
          <span className="stat-label">DLQ size</span>
          <span className={`stat-value ${status.dlqSize > 0 ? 'error-text' : ''}`}>{status.dlqSize}</span>
        </div>
      </div>

      <h4>Consumer lag</h4>
      <table className="data-table">
        <thead><tr><th>Group</th><th>Lag</th></tr></thead>
        <tbody>
          {Object.entries(status.consumerLag ?? {}).map(([group, lag]) => (
            <tr key={group}>
              <td><code>{group}</code></td>
              <td className={lag > 0 ? 'warn-text' : ''}>{lag}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h4>Consumers</h4>
      <table className="data-table">
        <thead><tr><th>Listener</th><th>State</th><th></th></tr></thead>
        <tbody>
          {(status.listeners ?? []).map((l) => (
            <tr key={l.id}>
              <td><code>{l.id}</code></td>
              <td>{!l.running ? 'stopped' : l.paused ? 'paused' : 'running'}</td>
              <td>
                {l.paused
                  ? <button className="ghost" onClick={() => onResume(l.id)}>Resume</button>
                  : <button className="ghost" onClick={() => onPause(l.id)}>Pause</button>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
