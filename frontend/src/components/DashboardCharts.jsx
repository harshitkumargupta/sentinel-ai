import { Bar, BarChart, Cell, LabelList, Pie, PieChart, ResponsiveContainer, Tooltip as RTooltip, XAxis, YAxis } from 'recharts';
import { Card, EmptyState } from './ui/index.js';

// Recharts is heavy, so this module is lazy-loaded by the dashboard (keeps it off the critical path).
// Severity is a *status* palette (reserved colours + always labelled), not a categorical cycle.
const SEV_VAR = { LOW: 'var(--sev-low)', MEDIUM: 'var(--sev-medium)', HIGH: 'var(--sev-high)', CRITICAL: 'var(--sev-critical)' };
const SEV_ORDER = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

const fmt = (n) => Number(n).toLocaleString();
const pct = (v, total) => (total > 0 ? Math.round((v / total) * 100) : 0);

/** Hover tooltip: count + share of total, in ink colours (identity carried by the swatch). */
function ShareTooltip({ active, payload, total, swatch }) {
  if (!active || !payload?.length) return null;
  const p = payload[0];
  const name = p.payload.name;
  const value = p.value;
  return (
    <div className="chart-tip">
      <span className="chart-tip__key">
        <span className="chart-tip__dot" style={{ background: swatch ? swatch(name) : p.color }} />{name}
      </span>
      <span className="chart-tip__val">{fmt(value)}{total ? ` · ${pct(value, total)}%` : ''}</span>
    </div>
  );
}

export default function DashboardCharts({ sevData, typeData }) {
  const sev = SEV_ORDER
    .map((name) => sevData.find((d) => d.name === name))
    .filter(Boolean)
    .filter((d) => d.value > 0);
  const sevTotal = sev.reduce((s, d) => s + d.value, 0);
  const typeMax = Math.max(1, ...typeData.map((d) => d.value));

  return (
    <>
      <Card title="Events by severity" subtitle={sevTotal ? `${fmt(sevTotal)} in the last 24h` : undefined}>
        {sevTotal === 0 ? (
          <EmptyState title="No events yet" message="Severity breakdown appears once events arrive." />
        ) : (
          <div className="chart-split">
            <div className="chart-donut">
              <ResponsiveContainer width="100%" height={168}>
                <PieChart>
                  <Pie data={sev} dataKey="value" nameKey="name" innerRadius={52} outerRadius={78}
                    paddingAngle={2} stroke="var(--panel)" strokeWidth={2} startAngle={90} endAngle={-270}>
                    {sev.map((d) => <Cell key={d.name} fill={SEV_VAR[d.name]} />)}
                  </Pie>
                  <RTooltip cursor={false} content={<ShareTooltip total={sevTotal} swatch={(n) => SEV_VAR[n]} />} />
                </PieChart>
              </ResponsiveContainer>
              <div className="chart-donut__center">
                <strong>{fmt(sevTotal)}</strong>
                <span>events</span>
              </div>
            </div>
            <ul className="chart-legend">
              {sev.map((d) => (
                <li key={d.name}>
                  <span className="chart-legend__dot" style={{ background: SEV_VAR[d.name] }} />
                  <span className="chart-legend__name">{d.name}</span>
                  <span className="chart-legend__val">{fmt(d.value)}</span>
                  <span className="chart-legend__pct">{pct(d.value, sevTotal)}%</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </Card>

      <Card title="Top event types" subtitle={typeData.length ? 'by volume, last 24h' : undefined}>
        {typeData.length === 0 ? (
          <EmptyState title="No event types yet" message="Event-type ranking appears once events arrive." />
        ) : (
          <div style={{ height: Math.max(140, typeData.length * 34) }}>
            <ResponsiveContainer>
              <BarChart data={typeData} layout="vertical" margin={{ left: 4, right: 36, top: 4, bottom: 4 }} barCategoryGap={8}>
                <XAxis type="number" hide domain={[0, typeMax]} />
                <YAxis type="category" dataKey="name" width={132} tickLine={false} axisLine={false}
                  tick={{ fill: 'var(--muted)', fontSize: 11 }} />
                <RTooltip cursor={{ fill: 'var(--panel-2)' }} content={<ShareTooltip />} />
                <Bar dataKey="value" radius={[0, 4, 4, 0]} fill="var(--accent)" barSize={14}
                  background={{ fill: 'var(--panel-2)', radius: 4 }}>
                  <LabelList dataKey="value" position="right" formatter={fmt}
                    style={{ fill: 'var(--text)', fontSize: 11, fontWeight: 600 }} />
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </Card>
    </>
  );
}
