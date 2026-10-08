import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import DataState from '../components/DataState.jsx';
import ParseReport from '../components/ParseReport.jsx';
import { useToast } from '../components/ui/index.js';
import { emitDataChanged } from '../hooks/useLiveRefresh.js';
import {
  getDemoInfo, listScenarios, listChain, runScenario, seedDemo, resetDemo, listDatasets, replayDataset,
} from '../services/demo.service.js';
import { messageFromError } from '../services/errors.js';

const STEP_STYLE = {
  pending: { icon: '○', cls: 'muted' },
  running: { icon: '◌', cls: '' },
  done: { icon: '✓', cls: '' },
  failed: { icon: '✗', cls: 'error-text' },
};

function RunResult({ r }) {
  if (!r) return null;
  return (
    <div className="small">
      {r.eventsGenerated} events → <strong>{r.alertsCreated} alert(s)</strong>
      {r.rulesFired?.length > 0 && <> ({r.rulesFired.join(', ')})</>}
      {r.incidentIds?.length > 0 && <> → incident{r.incidentIds.length > 1 ? 's' : ''}{' '}
        {r.incidentIds.map((id) => <Link key={id} to={`/offenses/${id}`} style={{ marginRight: 4 }}>#{id}</Link>)}</>}
      {r.highestSeverity && <span className="chip">{r.highestSeverity}</span>}
    </div>
  );
}

/**
 * Demo Center: one button per existing simulator scenario (events go through the real pipeline,
 * re-timed to now), a full-chain run with a live stepper, sample-log replay, seed and reset.
 */
export default function DemoCenterPage() {
  const { push } = useToast();
  const [info, setInfo] = useState(null);
  const [scenarios, setScenarios] = useState(null);
  const [chain, setChain] = useState([]);
  const [datasets, setDatasets] = useState([]);
  const [results, setResults] = useState({});
  const [running, setRunning] = useState(null);
  const [steps, setSteps] = useState([]);
  const [replay, setReplay] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    getDemoInfo().then((i) => {
      setInfo(i);
      if (!i.demoMode) return null;
      return Promise.all([listScenarios(), listChain(), listDatasets()]).then(([s, c, d]) => {
        setScenarios(s); setChain(c); setDatasets(d);
      });
    }).catch((e) => setError(messageFromError(e)));
  }, []);

  async function run(s) {
    setRunning(s.id);
    try {
      const r = await runScenario(s.id);
      setResults((p) => ({ ...p, [s.id]: r }));
      emitDataChanged('demo');
      push(`${s.label}: ${r.alertsCreated} alert(s), ${r.incidentIds.length} incident(s)`,
        { variant: r.alertsCreated > 0 ? 'success' : 'info' });
    } catch (e) {
      push(`${s.label} failed: ${messageFromError(e)}`, { variant: 'error' });
    } finally {
      setRunning(null);
    }
  }

  async function runChain() {
    setRunning('chain');
    setSteps(chain.map((c) => ({ ...c, state: 'pending', result: null })));
    let alerts = 0;
    for (let i = 0; i < chain.length; i++) {
      setSteps((st) => st.map((x, j) => (j === i ? { ...x, state: 'running' } : x)));
      try {
        const r = await runScenario(chain[i].id);
        alerts += r.alertsCreated;
        setSteps((st) => st.map((x, j) => (j === i ? { ...x, state: 'done', result: r } : x)));
        emitDataChanged('demo');
      } catch (e) {
        setSteps((st) => st.map((x, j) => (j === i ? { ...x, state: 'failed', error: messageFromError(e) } : x)));
      }
    }
    setRunning(null);
    push(`Full chain finished: ${alerts} alert(s) across ${chain.length} stages`, { variant: 'success' });
  }

  async function seed() {
    setRunning('seed');
    try {
      const r = await seedDemo();
      emitDataChanged('demo');
      push(`Seeded ${r.eventsGenerated} baseline events over the last 24 hours`, { variant: 'success' });
    } catch (e) { push(messageFromError(e), { variant: 'error' }); }
    finally { setRunning(null); }
  }

  async function reset() {
    if (!window.confirm('Remove all demo-generated events, alerts and incidents? Users, rules and real data are kept.')) return;
    setRunning('reset');
    try {
      const r = await resetDemo();
      setResults({}); setSteps([]); setReplay(null);
      emitDataChanged('demo');
      push(`Reset: removed ${r.incidents} incident(s), ${r.alerts} alert(s), ${r.events} event(s)`, { variant: 'success' });
    } catch (e) { push(messageFromError(e), { variant: 'error' }); }
    finally { setRunning(null); }
  }

  async function doReplay(d) {
    setRunning(`replay:${d.id}`);
    try {
      const r = await replayDataset(d.id);
      setReplay(r);
      emitDataChanged('demo');
      push(`${d.label}: ${r.report.accepted} events, ${r.alertsCreated} alert(s)`, { variant: 'success' });
    } catch (e) { push(messageFromError(e), { variant: 'error' }); }
    finally { setRunning(null); }
  }

  if (info && !info.demoMode) {
    return (
      <>
        <h2>Demo Center</h2>
        <p className="muted">Demo mode is off on this server. Start it with the <code>demo</code> profile
          (<code>./scripts/demo.sh up</code>) to use the Demo Center.</p>
      </>
    );
  }

  const busy = running !== null;
  return (
    <>
      <h2>Demo Center</h2>
      <p className="subtitle">Every button feeds events through the real ingestion, detection, risk and correlation
        pipeline. Nothing is mocked in the UI; no real system or network is touched.</p>
      {error && <p className="error-text">{error}</p>}

      <section className="panel">
        <div className="filters">
          <button onClick={seed} disabled={busy}>{running === 'seed' ? 'Seeding…' : 'Seed Sample Data (24h baseline)'}</button>
          <button className="ghost" onClick={reset} disabled={busy}>{running === 'reset' ? 'Resetting…' : 'Reset Demo Data'}</button>
          <Link to="/dashboard" className="ui-btn ui-btn--ghost">Open dashboard →</Link>
          <Link to="/offenses" className="ui-btn ui-btn--ghost">Open offenses →</Link>
        </div>
      </section>

      <section className="panel">
        <div className="brand-row" style={{ justifyContent: 'space-between' }}>
          <h3>Run Full Attack Chain</h3>
          <button onClick={runChain} disabled={busy || chain.length === 0}>
            {running === 'chain' ? 'Running…' : `Run ${chain.length} stages`}
          </button>
        </div>
        <ol className="stepper">
          {(steps.length ? steps : chain.map((c) => ({ ...c, state: 'pending' }))).map((s) => {
            const st = STEP_STYLE[s.state];
            return (
              <li key={s.id} className={st.cls}>
                <strong>{st.icon} {s.label}</strong> <span className="muted small">— {s.description}</span>
                {s.result && <RunResult r={s.result} />}
                {s.error && <div className="error-text small">{s.error}</div>}
              </li>
            );
          })}
        </ol>
      </section>

      <section className="panel">
        <h3>Scenarios</h3>
        <DataState loading={!scenarios && !error} error={null} empty={(scenarios ?? []).length === 0} emptyText="No scenarios.">
          <div className="scenario-grid">
            {(scenarios ?? []).map((s) => (
              <div key={s.id} className="panel">
                <div className="brand-row" style={{ justifyContent: 'space-between' }}>
                  <strong>{s.label}</strong>
                  <button onClick={() => run(s)} disabled={busy}>{running === s.id ? 'Running…' : 'Run'}</button>
                </div>
                <p className="muted small">{s.description}</p>
                {s.expectedRule && <span className="chip">expects {s.expectedRule}</span>}
                <RunResult r={results[s.id]} />
              </div>
            ))}
          </div>
        </DataState>
      </section>

      {datasets.length > 0 && (
        <section className="panel">
          <h3>Replay sample log files</h3>
          <div className="filters">
            {datasets.map((d) => (
              <button key={d.id} className="ghost" disabled={busy} title={d.description} onClick={() => doReplay(d)}>
                {running === `replay:${d.id}` ? 'Replaying…' : `Replay: ${d.label}`}
              </button>
            ))}
          </div>
          {replay && (
            <>
              <p className="small">Into <strong>{replay.sourceName}</strong>: {replay.alertsCreated} alert(s)
                {replay.rulesFired.length > 0 && <> ({replay.rulesFired.join(', ')})</>}, {replay.incidentIds.length} incident(s).</p>
              <ParseReport report={replay.report} />
            </>
          )}
        </section>
      )}
    </>
  );
}
