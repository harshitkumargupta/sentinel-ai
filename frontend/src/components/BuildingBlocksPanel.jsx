import { useEffect, useState } from 'react';
import DataState from './DataState.jsx';
import {
  getBuildingBlockVocabulary, createBuildingBlock, deleteBuildingBlock,
} from '../services/rules.service.js';
import { messageFromError } from '../services/errors.js';

const EMPTY_CONDITION = { field: 'sourceIp', op: 'IN_CIDR', values: '' };

/** Building blocks: reusable AND-ed conditions; admins add/delete, everyone sees where they are used. */
export default function BuildingBlocksPanel({ blocks, loading, error, canEdit, onChanged }) {
  const [vocab, setVocab] = useState({ fields: [], operators: [] });
  const [form, setForm] = useState({ name: '', description: '', conditions: [EMPTY_CONDITION] });
  const [formError, setFormError] = useState(null);

  useEffect(() => { getBuildingBlockVocabulary().then(setVocab).catch(() => {}); }, []);

  function setCondition(i, patch) {
    setForm((f) => ({ ...f, conditions: f.conditions.map((c, j) => (j === i ? { ...c, ...patch } : c)) }));
  }

  async function create(e) {
    e.preventDefault();
    setFormError(null);
    try {
      await createBuildingBlock({
        name: form.name,
        description: form.description || null,
        conditions: form.conditions.map((c) => ({
          field: c.field, op: c.op, values: c.values.split(',').map((v) => v.trim()).filter(Boolean),
        })),
      });
      setForm({ name: '', description: '', conditions: [EMPTY_CONDITION] });
      onChanged();
    } catch (err) {
      setFormError(messageFromError(err));
    }
  }

  async function remove(b) {
    if (!window.confirm(`Delete building block "${b.name}"?`)) return;
    try { await deleteBuildingBlock(b.id); onChanged(); } catch (err) { setFormError(messageFromError(err)); }
  }

  return (
    <>
      <DataState loading={loading} error={error} empty={blocks.length === 0} emptyText="No building blocks.">
        <table className="data-table">
          <thead><tr><th>Name</th><th>Conditions (all must match)</th><th>Used by</th>{canEdit && <th></th>}</tr></thead>
          <tbody>
            {blocks.map((b) => (
              <tr key={b.id}>
                <td><strong>{b.name}</strong>{b.description && <div className="muted small">{b.description}</div>}</td>
                <td>{b.conditions.map((c, i) => (
                  <div key={i}><code>{c.field} {c.op} [{c.values.join(', ')}]</code></div>
                ))}</td>
                <td>{b.usedByRules.length ? b.usedByRules.join(', ') : <span className="muted">—</span>}</td>
                {canEdit && <td><button className="ghost" disabled={b.usedByRules.length > 0}
                  title={b.usedByRules.length ? 'Remove it from the rules first' : ''}
                  onClick={() => remove(b)}>Delete</button></td>}
              </tr>
            ))}
          </tbody>
        </table>
      </DataState>

      {canEdit && (
        <form className="panel" onSubmit={create}>
          <h4>New building block</h4>
          <div className="filters">
            <input placeholder="Name" required maxLength={100} value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })} />
            <input placeholder="Description" maxLength={500} value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
          {form.conditions.map((c, i) => (
            <div className="filters" key={i}>
              <select value={c.field} aria-label="Field" onChange={(e) => setCondition(i, { field: e.target.value })}>
                {vocab.fields.map((f) => <option key={f}>{f}</option>)}
              </select>
              <select value={c.op} aria-label="Operator" onChange={(e) => setCondition(i, { op: e.target.value })}>
                {vocab.operators.map((o) => <option key={o}>{o}</option>)}
              </select>
              <input placeholder="values, comma-separated" value={c.values} aria-label="Values"
                onChange={(e) => setCondition(i, { values: e.target.value })} />
              {form.conditions.length > 1 && (
                <button type="button" className="ghost" onClick={() =>
                  setForm((f) => ({ ...f, conditions: f.conditions.filter((_, j) => j !== i) }))}>Remove</button>
              )}
            </div>
          ))}
          <div className="filters">
            <button type="button" className="ghost"
              onClick={() => setForm((f) => ({ ...f, conditions: [...f.conditions, EMPTY_CONDITION] }))}>+ Condition</button>
            <button type="submit">Create building block</button>
          </div>
          {formError && <p className="error-text">{formError}</p>}
        </form>
      )}
    </>
  );
}
