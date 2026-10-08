import { Fragment, useCallback, useEffect, useState } from 'react';
import DataState from '../components/DataState.jsx';
import SeverityBadge from '../components/SeverityBadge.jsx';
import MitreChip from '../components/MitreChip.jsx';
import RuleEditor from '../components/RuleEditor.jsx';
import RuleSandbox from '../components/RuleSandbox.jsx';
import BuildingBlocksPanel from '../components/BuildingBlocksPanel.jsx';
import { Tabs } from '../components/ui/index.js';
import { useAuth } from '../context/AuthContext.jsx';
import {
  listRules, setRuleEnabled, updateRule, backtestRule, listBuildingBlocks,
} from '../services/rules.service.js';
import { messageFromError } from '../services/errors.js';

function summary(rule) {
  try {
    const c = JSON.parse(rule.config || '{}');
    const parts = [];
    if (c.threshold) parts.push(`≥ ${c.threshold}`);
    if (c.windowSeconds) parts.push(`in ${c.windowSeconds}s`);
    if (c.groupBy) parts.push(`by ${c.groupBy}`);
    return { text: parts.join(' ') || 'defaults', blocks: c.buildingBlocks || [] };
  } catch {
    return { text: 'invalid config', blocks: [] };
  }
}

/** QRadar-style rules list: what each rule matches, enable/disable, structured edit, backtest. */
export default function RulesPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const [rules, setRules] = useState(null);
  const [blocks, setBlocks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editing, setEditing] = useState(null);
  const [backtests, setBacktests] = useState({});
  const [testing, setTesting] = useState(null);
  const canTest = hasRole('ANALYST', 'ADMIN');

  const load = useCallback(async () => {
    try {
      const [r, b] = await Promise.all([listRules(), listBuildingBlocks()]);
      setRules(r);
      setBlocks(b);
      setError(null);
    } catch (e) {
      setError(messageFromError(e));
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { load(); }, [load]);

  async function toggle(rule) {
    try { await setRuleEnabled(rule.id, !rule.enabled); load(); } catch (e) { setError(messageFromError(e)); }
  }

  async function save(rule, patch) {
    try {
      await updateRule(rule.id, patch);
      setEditing(null);
      load();
    } catch (e) {
      throw new Error(messageFromError(e));
    }
  }

  async function backtest(rule) {
    const to = new Date();
    const from = new Date(to.getTime() - 7 * 24 * 3600 * 1000);
    try {
      const res = await backtestRule(rule.id, { from: from.toISOString(), to: to.toISOString() });
      setBacktests((p) => ({ ...p, [rule.id]: `${res.alertsFired} alert(s) over ${res.eventsScanned} events (last 7 days)` }));
    } catch (e) {
      setBacktests((p) => ({ ...p, [rule.id]: messageFromError(e) }));
    }
  }

  const rulesTab = (
    <DataState loading={loading} error={error} empty={(rules ?? []).length === 0} emptyText="No rules.">
      <table className="data-table">
        <thead>
          <tr><th>Rule</th><th>Type</th><th>Matches</th><th>Building blocks</th><th>Severity</th><th>MITRE</th>
            <th>Enabled</th>{(isAdmin || canTest) && <th></th>}</tr>
        </thead>
        <tbody>
          {(rules ?? []).map((r) => {
            const s = summary(r);
            return (
              <Fragment key={r.id}>
                <tr>
                  <td><strong>{r.name}</strong><div className="muted small">v{r.version}</div></td>
                  <td><code>{r.ruleType}</code></td>
                  <td>{s.text}</td>
                  <td>{s.blocks.length ? s.blocks.map((b) => <span key={b} className="chip">{b}</span>) : '—'}</td>
                  <td><SeverityBadge severity={r.severity} /></td>
                  <td><MitreChip technique={r.mitreTechnique} /></td>
                  <td>{isAdmin
                    ? <button className="ghost" onClick={() => toggle(r)} aria-pressed={r.enabled}>{r.enabled ? 'On' : 'Off'}</button>
                    : (r.enabled ? 'On' : 'Off')}</td>
                  {!isAdmin && canTest && (
                    <td><button className="ghost" onClick={() => setTesting(testing === r.id ? null : r.id)}>Test</button></td>
                  )}
                  {isAdmin && (
                    <td>
                      <button className="ghost" onClick={() => setTesting(testing === r.id ? null : r.id)}>Test</button>
                      <button className="ghost" onClick={() => setEditing(editing === r.id ? null : r.id)}>Edit</button>
                      <button className="ghost" onClick={() => backtest(r)}>Backtest</button>
                      {backtests[r.id] && <div className="muted small">{backtests[r.id]}</div>}
                    </td>
                  )}
                </tr>
                {testing === r.id && (
                  <tr>
                    <td colSpan={8}>
                      <RuleSandbox rule={r} canSave={isAdmin} onClose={() => setTesting(null)}
                        onSaved={() => { setTesting(null); load(); }} />
                    </td>
                  </tr>
                )}
                {editing === r.id && (
                  <tr>
                    <td colSpan={8}>
                      <RuleEditor rule={r} buildingBlocks={blocks} onSave={(patch) => save(r, patch)}
                        onCancel={() => setEditing(null)} />
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </DataState>
  );

  return (
    <>
      <h2>Rules</h2>
      <p className="subtitle">Detection rules run on every ingested event. Changes apply immediately — no redeploy.</p>
      <Tabs tabs={[
        { id: 'rules', label: `Rules (${rules?.length ?? 0})`, content: rulesTab },
        { id: 'blocks', label: `Building blocks (${blocks.length})`, content: (
          <BuildingBlocksPanel blocks={blocks} loading={loading} error={error} canEdit={isAdmin} onChanged={load} />
        ) },
      ]} />
    </>
  );
}
