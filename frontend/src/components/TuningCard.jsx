import { useCallback, useEffect, useState } from 'react';
import { getTuningSuggestions, updateRule } from '../services/rules.service.js';
import { useAuth } from '../context/AuthContext.jsx';
import { messageFromError } from '../services/errors.js';

/** Dashboard card: rules ranked by false-positive rate with a suggested threshold change + Apply. */
export default function TuningCard() {
  const { hasRole } = useAuth();
  const [rules, setRules] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const load = useCallback(async () => {
    try {
      setRules(await getTuningSuggestions());
    } catch (e) {
      setError(messageFromError(e));
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  async function apply(rule) {
    setNotice(null);
    try {
      await updateRule(rule.ruleId, { config: rule.suggestion.suggestedConfig });
      setNotice(`Applied to '${rule.ruleName}'.`);
      load();
    } catch (e) {
      setError(messageFromError(e));
    }
  }

  const ranked = (rules || [])
    .filter((r) => r.sampleSize > 0)
    .sort((a, b) => b.fpRate - a.fpRate);

  return (
    <section className="panel">
      <h3>Detection tuning (analyst feedback)</h3>
      {error && <p className="error-text">{error}</p>}
      {notice && <p className="muted small">{notice}</p>}
      {rules && ranked.length === 0 && (
        <p className="muted">No reviewed incidents yet — mark incidents true/false positive to get suggestions.</p>
      )}
      {ranked.length > 0 && (
        <table className="data-table">
          <thead>
            <tr><th>Rule</th><th>FP rate</th><th>Sample</th><th>Suggestion</th><th></th></tr>
          </thead>
          <tbody>
            {ranked.map((r) => (
              <tr key={r.ruleId}>
                <td>{r.ruleName}</td>
                <td className={r.fpRate > 0.3 ? 'error-text' : ''}>{Math.round(r.fpRate * 100)}%</td>
                <td>{r.sampleSize}</td>
                <td className="muted small">
                  {r.enoughSamples
                    ? (r.suggestion ? r.suggestion.summary : 'no safe change found')
                    : `need ≥ sample size (have ${r.sampleSize})`}
                </td>
                <td>
                  {hasRole('ADMIN') && r.suggestion && (
                    <button className="ghost" onClick={() => apply(r)}>Apply</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
