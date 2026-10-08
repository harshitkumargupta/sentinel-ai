import { useEffect, useRef, useState } from 'react';
import { askIncident, listAiQuestions } from '../services/ai.service.js';
import { messageFromError } from '../services/errors.js';
import AiModeBadge from './AiModeBadge.jsx';

/**
 * "Ask AI" about this incident: fixed questions as chips plus free text, answered offline by the
 * local engine from the incident's own events. Evidence ids highlight the matching event rows.
 */
export default function AskAiPanel({ incidentId, onHighlight }) {
  const [questions, setQuestions] = useState([]);
  const [text, setText] = useState('');
  const [thread, setThread] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const endRef = useRef(null);

  useEffect(() => { listAiQuestions().then(setQuestions).catch(() => {}); }, []);
  useEffect(() => { endRef.current?.scrollIntoView?.({ block: 'nearest' }); }, [thread]);

  async function ask(payload) {
    setBusy(true);
    setError(null);
    try {
      const answer = await askIncident(incidentId, payload);
      setThread((t) => [...t, answer]);
      setText('');
    } catch (e) {
      setError(messageFromError(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel">
      <div className="brand-row" style={{ justifyContent: 'space-between' }}>
        <h3>Ask AI</h3>
        <AiModeBadge />
      </div>
      <div className="chip-row">
        {questions.map((q) => (
          <button key={q.intent} className="evidence-chip" disabled={busy} onClick={() => ask({ intent: q.intent })}>
            {q.label}
          </button>
        ))}
      </div>
      <div className="ask-thread" aria-live="polite">
        {thread.map((a, i) => (
          <div key={i} className="panel" style={{ marginTop: '0.5rem' }}>
            <p className="muted small">Q: {a.question}</p>
            <p>{a.answer}</p>
            {a.bullets?.length > 0 && <ul className="small">{a.bullets.map((b) => <li key={b}>{b}</li>)}</ul>}
            {a.evidenceEventIds?.length > 0 && (
              <button className="ghost small" onMouseEnter={() => onHighlight?.(a.evidenceEventIds)}
                onMouseLeave={() => onHighlight?.([])} onClick={() => onHighlight?.(a.evidenceEventIds)}>
                Show {a.evidenceEventIds.length} evidence event(s)
              </button>
            )}
          </div>
        ))}
        <div ref={endRef} />
      </div>
      {error && <p className="error-text">{error}</p>}
      <form className="filters" onSubmit={(e) => { e.preventDefault(); if (text.trim()) ask({ question: text.trim() }); }}>
        <input style={{ flex: 1 }} maxLength={300} placeholder="Ask about this incident, e.g. which IPs are involved?"
          value={text} onChange={(e) => setText(e.target.value)} aria-label="Question" />
        <button type="submit" disabled={busy || !text.trim()}>{busy ? 'Thinking…' : 'Ask'}</button>
      </form>
    </section>
  );
}
