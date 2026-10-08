import { useEffect, useState } from 'react';
import { createRule, sandboxRule, updateRule } from '../services/rules.service.js';
import { listDatasets } from '../services/demo.service.js';
import { messageFromError } from '../services/errors.js';

const RANGES = { '24h': 864e5, '7d': 7 * 864e5, '30d': 30 * 864e5 };

function Side({ title, side }) {
  return (
    <div className="panel">
      <h4>{title}: {side.alerts} alert(s)</h4>
      <code className="small">{side.config}</code>
      <ul className="breakdown">
        {side.samples.map((f, i) => (
          <li key={i}><span className="small">{f.message}
            <div className="muted small">{f.events.map((e) => `${e.type} ${e.username || ''} ${e.sourceIp || ''}`.trim()).join(' · ')}</div></span></li>
        ))}
      </ul>
    </div>
  );
}

/** "Test this rule": edit threshold/window without saving, replay a range or sample dataset, see the diff. */
export default function RuleSandbox({ rule, canSave, onSaved, onClose }) {
  let cfg = {};
  try { cfg = JSON.parse(rule.config || '{}'); } catch { /* keep empty */ }
  const [edited, setEdited] = useState({ threshold: cfg.threshold ?? '', windowSeconds: cfg.windowSeconds ?? '' });
  const [source, setSource] = useState('7d');
  const [datasets, setDatasets] = useState([]);
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  useEffect(() => { listDatasets().then(setDatasets).catch(() => setDatasets([])); }, []);

  const editedConfig = () => {
    const c = { ...cfg };
    if (edited.threshold !== '') c.threshold = Number(edited.threshold); else delete c.threshold;
    if (edited.windowSeconds !== '') c.windowSeconds = Number(edited.windowSeconds); else delete c.windowSeconds;
    return JSON.stringify(c);
  };

  async function run() {
    setBusy(true); setError(null);
    try {
      const body = { editedConfig: editedConfig() };
      if (RANGES[source]) { const to = new Date(); body.to = to.toISOString(); body.from = new Date(to - RANGES[source]).toISOString(); }
      else body.datasetId = source;
      setResult(await sandboxRule(rule.id, body));
    } catch (e) { setError(messageFromError(e)); }
    finally { setBusy(false); }
  }

  async function apply() {
    try { await updateRule(rule.id, { config: editedConfig() }); onSaved(); } catch (e) { setError(messageFromError(e)); }
  }
  async function saveAsNew() {
    const name = window.prompt('Name for the new rule', `${rule.name} (tuned)`);
    if (!name) return;
    try {
      await createRule({ name, ruleType: rule.ruleType, config: editedConfig(), severity: rule.severity, mitreTechnique: rule.mitreTechnique, enabled: false });
      onSaved();
    } catch (e) { setError(messageFromError(e)); }
  }

  return (
    <div className="panel rule-editor">
      <h4>Test this rule (sandbox — creates no alerts)</h4>
      <div className="filters">
        <label>Threshold <input type="number" min="1" value={edited.threshold} placeholder="default" onChange={(e) => setEdited({ ...edited, threshold: e.target.value })} /></label>
        <label>Window (s) <input type="number" min="1" value={edited.windowSeconds} placeholder="default" onChange={(e) => setEdited({ ...edited, windowSeconds: e.target.value })} /></label>
        <select value={source} onChange={(e) => setSource(e.target.value)} aria-label="Data to replay">
          <option value="24h">Stored events — last 24h</option><option value="7d">Stored events — last 7 days</option><option value="30d">Stored events — last 30 days</option>
          {datasets.map((d) => <option key={d.id} value={d.id}>Sample dataset — {d.label}</option>)}
        </select>
        <button onClick={run} disabled={busy}>{busy ? 'Running…' : 'Run sandbox'}</button>
        <button className="ghost" onClick={onClose}>Close</button>
      </div>
      {error && <p className="error-text">{error}</p>}
      {result && (
        <>
          <p><strong>{result.summary}</strong> <span className="muted small">({result.source})</span></p>
          {result.newlyAlerting.length > 0 && <p className="small">Newly alerting: {result.newlyAlerting.map((e) => <code key={e} style={{ marginRight: 6 }}>{e}</code>)}</p>}
          {result.noLongerAlerting.length > 0 && <p className="small">No longer alerting: {result.noLongerAlerting.map((e) => <code key={e} style={{ marginRight: 6 }}>{e}</code>)}</p>}
          <div className="panel-grid"><Side title="Current rule" side={result.current} /><Side title="Edited rule" side={result.edited} /></div>
          {canSave && (
            <div className="filters">
              <button onClick={apply}>Apply to this rule</button>
              <button className="ghost" onClick={saveAsNew}>Save as new rule (disabled)</button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
