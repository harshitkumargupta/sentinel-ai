import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import NavBar from '../components/NavBar.jsx';
import DataState from '../components/DataState.jsx';
import { getEvaluation } from '../services/evaluation.service.js';
import { messageFromError } from '../services/errors.js';

function pct(v) {
  return `${(v * 100).toFixed(1)}%`;
}

export default function EvaluationPage() {
  const [params] = useSearchParams();
  const [runId, setRunId] = useState(params.get('runId') || '');
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  async function load() {
    if (!runId) return;
    setLoading(true);
    setError(null);
    try {
      setResult(await getEvaluation(runId));
    } catch (e) {
      setError(messageFromError(e));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="app-shell">
      <NavBar />
      <main className="content">
        <h2>Detection evaluation</h2>
        <div className="filters">
          <input placeholder="run id" value={runId} onChange={(e) => setRunId(e.target.value)} style={{ minWidth: 280 }} />
          <button onClick={load}>Evaluate</button>
        </div>

        <DataState loading={loading} error={error} empty={!result} emptyText="Enter a run id and evaluate.">
          {result && (
            <>
              <section className="tiles">
                <div className="tile"><span className="tile-value">{pct(result.overall.precision)}</span><span className="tile-label">Precision</span></div>
                <div className="tile"><span className="tile-value">{pct(result.overall.recall)}</span><span className="tile-label">Recall</span></div>
                <div className="tile"><span className="tile-value">{result.overall.f1.toFixed(3)}</span><span className="tile-label">F1</span></div>
                <div className="tile"><span className="tile-value">{result.meanDetectionLatencySeconds}s</span><span className="tile-label">Mean latency</span></div>
              </section>
              <table className="data-table">
                <thead><tr><th>Rule</th><th>Precision</th><th>Recall</th><th>F1</th><th>TP</th><th>FP</th><th>FN</th></tr></thead>
                <tbody>
                  {Object.entries(result.perRule).map(([rule, m]) => (
                    <tr key={rule}>
                      <td>{rule}</td><td>{pct(m.precision)}</td><td>{pct(m.recall)}</td>
                      <td>{m.f1.toFixed(3)}</td><td>{m.tp}</td><td>{m.fp}</td><td>{m.fn}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
        </DataState>
      </main>
    </div>
  );
}
