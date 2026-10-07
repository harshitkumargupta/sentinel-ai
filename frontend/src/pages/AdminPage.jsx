import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import DataState from '../components/DataState.jsx';
import SeverityBadge from '../components/SeverityBadge.jsx';
import MitreChip from '../components/MitreChip.jsx';
import { runSimulator } from '../services/simulator.service.js';
import { listRules, setRuleEnabled, updateRule, backtestRule } from '../services/rules.service.js';
import { messageFromError } from '../services/errors.js';

// Covers the simulator's fixed time anchor (2026-02-01).
const BT_FROM = '2026-01-01T00:00:00Z';
const BT_TO = '2027-01-01T00:00:00Z';

export default function AdminPage() {
  const navigate = useNavigate();
  const [seed, setSeed] = useState(42);
  const [intensity, setIntensity] = useState(5);
  const [runResult, setRunResult] = useState(null);
  const [runError, setRunError] = useState(null);
  const [running, setRunning] = useState(false);

  const [rules, setRules] = useState(null);
  const [rulesError, setRulesError] = useState(null);
  const [rulesLoading, setRulesLoading] = useState(true);
  const [edits, setEdits] = useState({});
  const [backtests, setBacktests] = useState({});

  async function loadRules() {
    setRulesError(null);
    try {
      setRules(await listRules());
    } catch (e) {
      setRulesError(messageFromError(e));
    } finally {
      setRulesLoading(false);
    }
  }

  useEffect(() => { loadRules(); }, []);

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

  async function toggle(rule) {
    try {
      await setRuleEnabled(rule.id, !rule.enabled);
      loadRules();
    } catch (e) {
      setRulesError(messageFromError(e));
    }
  }

  async function saveConfig(rule) {
    try {
      await updateRule(rule.id, { config: edits[rule.id] ?? rule.config });
      setEdits((prev) => ({ ...prev, [rule.id]: undefined }));
      loadRules();
    } catch (e) {
      setRulesError(messageFromError(e));
    }
  }

  async function runBacktest(rule) {
    try {
      const res = await backtestRule(rule.id, { from: BT_FROM, to: BT_TO });
      setBacktests((prev) => ({ ...prev, [rule.id]: res }));
    } catch (e) {
      setBacktests((prev) => ({ ...prev, [rule.id]: { error: messageFromError(e) } }));
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
          <DataState loading={rulesLoading} error={rulesError} empty={(rules ?? []).length === 0}>
            <table className="data-table">
              <thead>
                <tr><th>Name</th><th>Type</th><th>Severity</th><th>MITRE</th><th>Enabled</th><th>Config</th><th></th></tr>
              </thead>
              <tbody>
                {(rules ?? []).map((r) => (
                  <tr key={r.id}>
                    <td>{r.name}</td>
                    <td>{r.ruleType}</td>
                    <td><SeverityBadge severity={r.severity} /></td>
                    <td><MitreChip technique={r.mitreTechnique} /></td>
                    <td><button className="ghost" onClick={() => toggle(r)}>{r.enabled ? 'On' : 'Off'}</button></td>
                    <td>
                      <textarea
                        className="config-edit"
                        value={edits[r.id] ?? r.config ?? ''}
                        onChange={(e) => setEdits((prev) => ({ ...prev, [r.id]: e.target.value }))}
                      />
                    </td>
                    <td>
                      <button className="ghost" onClick={() => saveConfig(r)}>Save</button>
                      <button className="ghost" onClick={() => runBacktest(r)}>Backtest</button>
                      {backtests[r.id] && (
                        <div className="muted small">
                          {backtests[r.id].error
                            ? backtests[r.id].error
                            : `${backtests[r.id].alertsFired}/${backtests[r.id].eventsScanned} fired`}
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </DataState>
        </section>
    </>
  );
}
