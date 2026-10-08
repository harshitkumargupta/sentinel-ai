import { useCallback, useEffect, useState } from 'react';
import DataState from '../components/DataState.jsx';
import PipelineStatusCard from '../components/PipelineStatusCard.jsx';
import {
  getPipelineStatus, listDlq, replayDlq, replayAllDlq,
  pauseConsumer, resumeConsumer, publishBurst,
} from '../services/pipeline.service.js';
import { messageFromError } from '../services/errors.js';

/** Admin view of the Kafka pipeline: live status, chaos controls, and the dead-letter queue. */
export default function PipelinePage() {
  const [status, setStatus] = useState(null);
  const [statusError, setStatusError] = useState(null);
  const [disabled, setDisabled] = useState(false);
  const [loading, setLoading] = useState(true);

  const [dlq, setDlq] = useState(null);
  const [dlqError, setDlqError] = useState(null);
  const [dlqLoading, setDlqLoading] = useState(true);

  const [burstCount, setBurstCount] = useState(1000);
  const [notice, setNotice] = useState(null);

  const loadStatus = useCallback(async () => {
    try {
      setStatus(await getPipelineStatus());
      setStatusError(null);
    } catch (e) {
      if (e?.response?.status === 404) {
        setDisabled(true);
      } else {
        setStatusError(messageFromError(e));
      }
    } finally {
      setLoading(false);
    }
  }, []);

  const loadDlq = useCallback(async () => {
    try {
      setDlq(await listDlq({ page: 0, size: 50 }));
      setDlqError(null);
    } catch (e) {
      if (e?.response?.status !== 404) {
        setDlqError(messageFromError(e));
      }
    } finally {
      setDlqLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStatus();
    loadDlq();
    const t = setInterval(loadStatus, 3000); // live-ish lag/backlog
    return () => clearInterval(t);
  }, [loadStatus, loadDlq]);

  async function run(action, after) {
    setNotice(null);
    try {
      const result = await action();
      if (after) after(result);
      await loadStatus();
      await loadDlq();
    } catch (e) {
      setNotice(messageFromError(e));
    }
  }

  if (disabled) {
    return (
      <>
        <h2>Pipeline</h2>
        <div className="state-box muted">
          The Kafka pipeline is disabled on this backend. Start it with{' '}
          <code>docker compose up -d</code> and <code>KAFKA_ENABLED=true</code>.
        </div>
      </>
    );
  }

  return (
    <>
        <h2>Pipeline</h2>
        {notice && <p className="error-text">{notice}</p>}

        <section className="panel">
          <h3>Status</h3>
          <DataState loading={loading} error={statusError} empty={false}>
            <PipelineStatusCard
              status={status}
              onPause={(id) => run(() => pauseConsumer(id))}
              onResume={(id) => run(() => resumeConsumer(id))}
            />
          </DataState>
        </section>

        <section className="panel">
          <h3>Chaos</h3>
          <div className="filters">
            <label>Burst size
              <input type="number" value={burstCount}
                     onChange={(e) => setBurstCount(e.target.value)} />
            </label>
            <button onClick={() => run(
              () => publishBurst(Number(burstCount)),
              (r) => setNotice(`Published ${r.published} events to ${r.topic}`),
            )}>Publish burst</button>
          </div>
          <p className="muted small">
            Pause a consumer above, publish a burst and watch its lag grow; resume to drain it.
          </p>
        </section>

        <section className="panel">
          <h3>Dead-letter queue</h3>
          <div className="filters">
            <button onClick={() => run(
              () => replayAllDlq(),
              (r) => setNotice(`Replayed ${r.replayed} dead letters`),
            )} disabled={(dlq?.content ?? []).length === 0}>Replay all</button>
          </div>
          <DataState loading={dlqLoading} error={dlqError}
                     empty={(dlq?.content ?? []).length === 0} emptyText="No dead letters. 🎉">
            <table className="data-table">
              <thead>
                <tr><th>ID</th><th>Topic</th><th>Handler</th><th>Attempts</th><th>Error</th><th></th></tr>
              </thead>
              <tbody>
                {(dlq?.content ?? []).map((m) => (
                  <tr key={m.id}>
                    <td>{m.id}</td>
                    <td><code>{m.originalTopic}</code></td>
                    <td>{m.handler}</td>
                    <td>{m.attempts}</td>
                    <td className="muted small">{m.error}</td>
                    <td>
                      <button className="ghost" onClick={() => run(
                        () => replayDlq(m.id),
                        () => setNotice(`Replayed dead letter #${m.id}`),
                      )}>Replay</button>
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
