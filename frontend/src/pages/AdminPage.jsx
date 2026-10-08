import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { runSimulator } from '../services/simulator.service.js';
import { messageFromError } from '../services/errors.js';

export default function AdminPage() {
  const navigate = useNavigate();
  const [seed, setSeed] = useState(42);
  const [intensity, setIntensity] = useState(5);
  const [runResult, setRunResult] = useState(null);
  const [runError, setRunError] = useState(null);
  const [running, setRunning] = useState(false);

  async function handleRun() {
    setRunning(true);
    setRunError(null);
    try {
      setRunResult(await runSimulator({ seed: Number(seed), intensity: Number(intensity) }));
    } catch (e) {
      setRunError(messageFromError(e));
    } finally {
      setRunning(false);
    }
  }

  return (
    <>
        <h2>Admin</h2>

        <section className="panel">
          <h3>Simulator</h3>
          <div className="filters">
            <label>Seed <input type="number" value={seed} onChange={(e) => setSeed(e.target.value)} /></label>
            <label>Intensity <input type="number" value={intensity} onChange={(e) => setIntensity(e.target.value)} /></label>
            <button onClick={handleRun} disabled={running}>{running ? 'Running…' : 'Run all scenarios'}</button>
          </div>
          {runError && <p className="error-text">{runError}</p>}
          {runResult && (
            <p>
              Run <code>{runResult.runId}</code> generated {runResult.eventsGenerated} events.{' '}
              <button className="ghost" onClick={() => navigate(`/evaluation?runId=${runResult.runId}`)}>
                View evaluation
              </button>
            </p>
          )}
        </section>

        <section className="panel">
          <h3>Detection rules</h3>
          <p className="muted">Rules and building blocks are managed on the <Link to="/rules">Rules page</Link>.</p>
        </section>
    </>
  );
}
