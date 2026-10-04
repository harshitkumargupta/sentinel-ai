import { useCallback, useEffect, useRef, useState } from 'react';
import { investigate, listAnalyses, getAnalysis, reviewAnalysis } from '../services/ai.service.js';
import { messageFromError } from '../services/errors.js';

/**
 * "Investigate with AI" panel for the incident page. Shows progress, the evidence-validated summary
 * with confidence/faithfulness and VALID/FALLBACK badges, claims with clickable evidence chips
 * (which highlight the matching event rows via onHighlight), and recommendations with review actions.
 */
export default function AiInvestigationPanel({ incidentId, canReview, onHighlight }) {
  const [analysis, setAnalysis] = useState(null);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState(null);
  const pollRef = useRef(null);

  const loadLatest = useCallback(async () => {
    try {
      const list = await listAnalyses(incidentId);
      setAnalysis(list && list.length ? list[0] : null);
    } catch (e) {
      setError(messageFromError(e));
    } finally {
      setLoading(false);
    }
  }, [incidentId]);

  useEffect(() => {
    loadLatest();
    return () => clearInterval(pollRef.current);
  }, [loadLatest]);

  async function run() {
    setError(null);
    setRunning(true);
    try {
      const { analysisId } = await investigate(incidentId);
      clearInterval(pollRef.current);
      pollRef.current = setInterval(async () => {
        try {
          const a = await getAnalysis(analysisId);
          setAnalysis(a);
          if (a.state === 'COMPLETE' || a.state === 'FAILED') {
            clearInterval(pollRef.current);
            setRunning(false);
          }
        } catch (e) {
          clearInterval(pollRef.current);
          setRunning(false);
          setError(messageFromError(e));
        }
      }, 1200);
    } catch (e) {
      setRunning(false);
      setError(messageFromError(e));
    }
  }

  async function review(decision) {
    try {
      setAnalysis(await reviewAnalysis(analysis.id, decision, null));
    } catch (e) {
      setError(messageFromError(e));
    }
  }

  const out = analysis?.output;
  const badgeClass = { VALID: 'badge-valid', FALLBACK: 'badge-fallback', REJECTED: 'badge-rejected' };

  return (
    <section className="panel">
      <div className="brand-row" style={{ justifyContent: 'space-between' }}>
        <h3>AI investigation</h3>
        <button onClick={run} disabled={running}>
          {running ? 'Investigating…' : (analysis ? 'Re-investigate with AI' : 'Investigate with AI')}
        </button>
      </div>

      {error && <p className="error-text">{error}</p>}
      {loading && <div className="state-box">Loading…</div>}
      {!loading && !analysis && !running && (
        <p className="muted">No analysis yet. Run an AI investigation to get an evidence-validated summary.</p>
      )}
      {running && !out && <div className="state-box">Running the AI pipeline…</div>}

      {analysis && out && (
        <>
          <div className="chip-row">
            <span className={`ai-badge ${badgeClass[analysis.validationStatus] || ''}`}>
              {analysis.validationStatus}
            </span>
            <span className="chip">confidence {fmtPct(out.confidence)}</span>
            <span className="chip">faithfulness {fmtScore(analysis.faithfulness)}</span>
            <span className="chip">review: {analysis.reviewStatus}</span>
            {analysis.modelName && <span className="chip muted">{analysis.modelName}</span>}
            {analysis.injectionDetected && (
              <span className="ai-badge badge-rejected">⚠ prompt-injection flagged</span>
            )}
          </div>

          <p>{out.summary}</p>

          {out.hypotheses?.length > 0 && (
            <>
              <h4>Hypotheses</h4>
              <ul className="breakdown">
                {out.hypotheses.map((h, i) => <li key={i}><span>{h}</span></li>)}
              </ul>
            </>
          )}

          {out.claims?.length > 0 && (
            <>
              <h4>Claims &amp; evidence</h4>
              <ul className="breakdown">
                {out.claims.map((c, i) => (
                  <li key={i}>
                    <span>{c.text}</span>
                    <span className="chip-row">
                      {(c.evidenceEventIds || []).map((id) => (
                        <button key={id} className="evidence-chip"
                                onMouseEnter={() => onHighlight?.(c.evidenceEventIds)}
                                onMouseLeave={() => onHighlight?.([])}
                                onClick={() => onHighlight?.([id])}>
                          #{id}
                        </button>
                      ))}
                    </span>
                  </li>
                ))}
              </ul>
            </>
          )}

          {out.recommendations?.length > 0 && (
            <>
              <h4>Recommendations {analysis.reviewStatus === 'PENDING' ? '(proposed)' : `(${analysis.reviewStatus})`}</h4>
              <table className="data-table">
                <thead><tr><th>Action</th><th>Target</th><th>Reason</th></tr></thead>
                <tbody>
                  {out.recommendations.map((r, i) => (
                    <tr key={i}>
                      <td><code>{r.action}</code></td>
                      <td>{r.target}</td>
                      <td className="muted small">{r.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {canReview && analysis.reviewStatus === 'PENDING' && (
                <div className="filters">
                  <button onClick={() => review('APPROVE')}>Approve</button>
                  <button className="ghost" onClick={() => review('REJECT')}>Reject</button>
                  <button className="ghost" onClick={() => review('MODIFY')}>Modify</button>
                </div>
              )}
            </>
          )}
        </>
      )}
    </section>
  );
}

function fmtPct(v) {
  return v == null ? '—' : `${Math.round(v * 100)}%`;
}
function fmtScore(v) {
  return v == null ? '—' : Number(v).toFixed(2);
}
