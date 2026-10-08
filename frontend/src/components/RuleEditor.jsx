import { useMemo, useState } from 'react';

const GROUP_BY = ['', 'username', 'sourceIp', 'entityKey'];
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

function parseConfig(raw) {
  try { const v = JSON.parse(raw || '{}'); return v && typeof v === 'object' && !Array.isArray(v) ? v : {}; }
  catch { return {}; }
}

/**
 * Structured editor for one rule: severity, MITRE, threshold / window / groupBy, building blocks,
 * and an advanced JSON view of the remaining config keys (rule-type specific, e.g. minBytes).
 * The server validates everything again; errors come back through onSave's rejection.
 */
export default function RuleEditor({ rule, buildingBlocks, onSave, onCancel }) {
  const initial = useMemo(() => parseConfig(rule.config), [rule.config]);
  const { threshold, windowSeconds, groupBy, buildingBlocks: refs, ...rest } = initial;
  const [form, setForm] = useState({
    severity: rule.severity,
    mitreTechnique: rule.mitreTechnique || '',
    threshold: threshold ?? '',
    windowSeconds: windowSeconds ?? '',
    groupBy: groupBy ?? '',
    blocks: refs ?? [],
    advanced: JSON.stringify(rest, null, 2),
  });
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  function toggleBlock(name) {
    setForm((f) => ({ ...f, blocks: f.blocks.includes(name) ? f.blocks.filter((b) => b !== name) : [...f.blocks, name] }));
  }

  async function save(e) {
    e.preventDefault();
    let extra;
    try { extra = JSON.parse(form.advanced || '{}'); } catch { setError('Advanced config must be valid JSON.'); return; }
    const config = { ...extra };
    if (form.threshold !== '') config.threshold = Number(form.threshold);
    if (form.windowSeconds !== '') config.windowSeconds = Number(form.windowSeconds);
    if (form.groupBy) config.groupBy = form.groupBy;
    if (form.blocks.length) config.buildingBlocks = form.blocks;
    setSaving(true);
    setError(null);
    try {
      await onSave({ severity: form.severity, mitreTechnique: form.mitreTechnique.trim(), config: JSON.stringify(config) });
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="panel rule-editor" onSubmit={save}>
      <div className="filters">
        <label>Severity
          <select value={form.severity} onChange={(e) => setForm({ ...form, severity: e.target.value })}>
            {SEVERITIES.map((s) => <option key={s}>{s}</option>)}
          </select>
        </label>
        <label>MITRE technique
          <input value={form.mitreTechnique} maxLength={20} placeholder="T1110"
            onChange={(e) => setForm({ ...form, mitreTechnique: e.target.value })} />
        </label>
        <label>Threshold
          <input type="number" min="1" value={form.threshold} placeholder="default"
            onChange={(e) => setForm({ ...form, threshold: e.target.value })} />
        </label>
        <label>Window (seconds)
          <input type="number" min="1" value={form.windowSeconds} placeholder="default"
            onChange={(e) => setForm({ ...form, windowSeconds: e.target.value })} />
        </label>
        <label>Group by
          <select value={form.groupBy} onChange={(e) => setForm({ ...form, groupBy: e.target.value })}>
            {GROUP_BY.map((g) => <option key={g} value={g}>{g || 'default'}</option>)}
          </select>
        </label>
      </div>
      <fieldset className="filters">
        <legend className="muted small">Building blocks (all must match for the rule to evaluate an event)</legend>
        {buildingBlocks.length === 0 && <span className="muted small">None defined.</span>}
        {buildingBlocks.map((b) => (
          <label key={b.id} className="chip" title={b.description || ''}>
            <input type="checkbox" checked={form.blocks.includes(b.name)} onChange={() => toggleBlock(b.name)} /> {b.name}
          </label>
        ))}
      </fieldset>
      <label className="muted small">Advanced config (other keys, JSON)
        <textarea className="config-edit" rows={3} value={form.advanced}
          onChange={(e) => setForm({ ...form, advanced: e.target.value })} />
      </label>
      {error && <p className="error-text">{error}</p>}
      <div className="filters">
        <button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
        <button type="button" className="ghost" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
