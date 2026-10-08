import { useCallback, useEffect, useState } from 'react';
import DataState from '../components/DataState.jsx';
import { useToast } from '../components/ui/index.js';
import { useAuth } from '../context/AuthContext.jsx';
import {
  listReportTypes, generateReport, listReports, deleteReport, downloadReport,
  listSchedules, saveSchedule, deleteSchedule, runSchedule,
} from '../services/reports.service.js';
import { messageFromError } from '../services/errors.js';

const DAYS = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const dateInput = (d) => d.toISOString().slice(0, 10);
const EMPTY_SCHEDULE = { name: '', type: 'INCIDENT_SUMMARY', format: 'PDF', frequency: 'WEEKLY', dayOfWeek: 1, hourUtc: 7 };

function kb(bytes) {
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

/** Reports: generate PDF/CSV for a date range, download history, and daily/weekly schedules. */
export default function ReportsPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const { push } = useToast();
  const [types, setTypes] = useState([]);
  const [form, setForm] = useState(() => {
    const to = new Date();
    return { type: 'INCIDENT_SUMMARY', format: 'PDF', from: dateInput(new Date(to.getTime() - 7 * 864e5)), to: dateInput(to) };
  });
  const [generating, setGenerating] = useState(false);
  const [reports, setReports] = useState(null);
  const [schedules, setSchedules] = useState([]);
  const [schedule, setSchedule] = useState(EMPTY_SCHEDULE);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      const [r, s] = await Promise.all([listReports(), listSchedules()]);
      setReports(r); setSchedules(s); setError(null);
    } catch (e) { setError(messageFromError(e)); }
  }, []);
  useEffect(() => { listReportTypes().then(setTypes).catch(() => {}); load(); }, [load]);

  const title = (id) => types.find((t) => t.id === id)?.title || id;

  async function generate(e) {
    e.preventDefault();
    setGenerating(true);
    try {
      // Whole days in UTC: from 00:00 of the first day to 24:00 of the last.
      const from = new Date(`${form.from}T00:00:00Z`).toISOString();
      const to = new Date(new Date(`${form.to}T00:00:00Z`).getTime() + 864e5).toISOString();
      const r = await generateReport({ type: form.type, format: form.format, from, to });
      if (r.status === 'COMPLETED') {
        push(`${title(r.type)} ready (${r.rows} rows)`, { variant: 'success' });
        await downloadReport(r);
      } else {
        push(`Report failed: ${r.error}`, { variant: 'error' });
      }
      load();
    } catch (err) { setError(messageFromError(err)); }
    finally { setGenerating(false); }
  }

  async function act(fn, ok) {
    try { await fn(); if (ok) push(ok, { variant: 'success' }); load(); } catch (e) { setError(messageFromError(e)); }
  }

  async function createSchedule(e) {
    e.preventDefault();
    const body = { ...schedule, dayOfWeek: schedule.frequency === 'WEEKLY' ? Number(schedule.dayOfWeek) : null, hourUtc: Number(schedule.hourUtc) };
    await act(async () => { await saveSchedule(null, body); setSchedule(EMPTY_SCHEDULE); }, 'Schedule saved');
  }

  return (
    <>
      <h2>Reports</h2>
      {error && <p className="error-text">{error}</p>}

      <form className="panel" onSubmit={generate}>
        <h3>Generate a report</h3>
        <div className="filters">
          <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })} aria-label="Report type">
            {types.map((t) => <option key={t.id} value={t.id}>{t.title}</option>)}
          </select>
          <label>From <input type="date" value={form.from} max={form.to} onChange={(e) => setForm({ ...form, from: e.target.value })} required /></label>
          <label>To <input type="date" value={form.to} min={form.from} onChange={(e) => setForm({ ...form, to: e.target.value })} required /></label>
          <select value={form.format} onChange={(e) => setForm({ ...form, format: e.target.value })} aria-label="Format">
            <option value="PDF">PDF</option><option value="CSV">CSV</option>
          </select>
          <button type="submit" disabled={generating}>{generating ? 'Generating…' : 'Generate'}</button>
        </div>
        <p className="muted small">Dates are whole days in UTC. Generated offline (PDFBox / CSV); files are kept below.</p>
      </form>

      <section className="panel">
        <h3>Generated reports</h3>
        <DataState loading={!reports && !error} error={null} empty={(reports ?? []).length === 0} emptyText="No reports yet.">
          <table className="data-table">
            <thead><tr><th>Report</th><th>Range (UTC)</th><th>Format</th><th>Rows</th><th>Size</th><th>Created</th><th></th></tr></thead>
            <tbody>
              {(reports ?? []).map((r) => (
                <tr key={r.id}>
                  <td>{title(r.type)}{r.scheduleId && <span className="chip">scheduled</span>}
                    {r.status === 'FAILED' && <div className="error-text small">Failed: {r.error}</div>}</td>
                  <td className="small">{r.from.slice(0, 10)} → {r.to.slice(0, 10)}</td>
                  <td>{r.format}</td><td>{r.rows}</td><td>{r.status === 'COMPLETED' ? kb(r.sizeBytes) : '—'}</td>
                  <td className="small">{new Date(r.createdAt).toLocaleString()}</td>
                  <td>
                    {r.status === 'COMPLETED' && <button className="ghost" onClick={() => act(() => downloadReport(r))}>Download</button>}
                    {isAdmin && <button className="ghost" onClick={() => act(() => deleteReport(r.id))}>Delete</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </DataState>
      </section>

      <section className="panel">
        <h3>Scheduled reports</h3>
        {schedules.length === 0 && <p className="muted small">No schedules.</p>}
        {schedules.length > 0 && (
          <table className="data-table">
            <thead><tr><th>Name</th><th>Report</th><th>When (UTC)</th><th>Last run</th><th>Next run</th><th>On</th><th></th></tr></thead>
            <tbody>
              {schedules.map((s) => (
                <tr key={s.id}>
                  <td>{s.name}</td><td>{title(s.type)} · {s.format}</td>
                  <td>{s.frequency === 'WEEKLY' ? `${DAYS[s.dayOfWeek]}s` : 'Daily'} at {String(s.hourUtc).padStart(2, '0')}:00</td>
                  <td className="small">{s.lastRunAt ? new Date(s.lastRunAt).toLocaleString() : '—'}</td>
                  <td className="small">{new Date(s.nextRunAt).toLocaleString()}</td>
                  <td>{isAdmin
                    ? <button className="ghost" onClick={() => act(() => saveSchedule(s.id, {
                      name: s.name, type: s.type, format: s.format, frequency: s.frequency, dayOfWeek: s.dayOfWeek,
                      hourUtc: s.hourUtc, enabled: !s.enabled,
                    }))}>{s.enabled ? 'On' : 'Off'}</button>
                    : (s.enabled ? 'On' : 'Off')}</td>
                  <td>
                    <button className="ghost" onClick={() => act(() => runSchedule(s.id), 'Report generated')}>Run now</button>
                    {isAdmin && <button className="ghost" onClick={() => act(() => deleteSchedule(s.id))}>Delete</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {isAdmin && (
          <form className="filters" onSubmit={createSchedule} style={{ marginTop: '0.75rem' }}>
            <input placeholder="Schedule name" required maxLength={100} value={schedule.name}
              onChange={(e) => setSchedule({ ...schedule, name: e.target.value })} />
            <select value={schedule.type} onChange={(e) => setSchedule({ ...schedule, type: e.target.value })} aria-label="Scheduled report type">
              {types.map((t) => <option key={t.id} value={t.id}>{t.title}</option>)}
            </select>
            <select value={schedule.format} onChange={(e) => setSchedule({ ...schedule, format: e.target.value })} aria-label="Scheduled format">
              <option>PDF</option><option>CSV</option>
            </select>
            <select value={schedule.frequency} onChange={(e) => setSchedule({ ...schedule, frequency: e.target.value })} aria-label="Frequency">
              <option value="DAILY">Daily</option><option value="WEEKLY">Weekly</option>
            </select>
            {schedule.frequency === 'WEEKLY' && (
              <select value={schedule.dayOfWeek} onChange={(e) => setSchedule({ ...schedule, dayOfWeek: e.target.value })} aria-label="Day">
                {DAYS.slice(1).map((d, i) => <option key={d} value={i + 1}>{d}</option>)}
              </select>
            )}
            <label>at <input type="number" min="0" max="23" value={schedule.hourUtc} style={{ width: 60 }}
              onChange={(e) => setSchedule({ ...schedule, hourUtc: e.target.value })} />:00 UTC</label>
            <button type="submit">Add schedule</button>
          </form>
        )}
      </section>
    </>
  );
}
