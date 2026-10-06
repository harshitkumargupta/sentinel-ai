import { Bar, BarChart, Cell, Pie, PieChart, ResponsiveContainer, Tooltip as RTooltip, XAxis, YAxis } from 'recharts';
import { Card } from './ui/index.js';

// Recharts is heavy, so this module is lazy-loaded by the dashboard (keeps it off the critical path).
const SEV_VAR = { LOW: 'var(--sev-low)', MEDIUM: 'var(--sev-medium)', HIGH: 'var(--sev-high)', CRITICAL: 'var(--sev-critical)' };
const CHART = ['var(--chart-1)', 'var(--chart-2)', 'var(--chart-3)', 'var(--chart-4)', 'var(--chart-5)', 'var(--chart-6)'];

export default function DashboardCharts({ sevData, typeData }) {
  return (
    <>
      <Card title="Events by severity">
        <div style={{ height: 200 }}>
          <ResponsiveContainer>
            <PieChart>
              <Pie data={sevData} dataKey="value" nameKey="name" innerRadius={45} outerRadius={75} paddingAngle={2}>
                {sevData.map((d) => <Cell key={d.name} fill={SEV_VAR[d.name] || 'var(--chart-1)'} />)}
              </Pie>
              <RTooltip contentStyle={{ background: 'var(--bg-elev)', border: '1px solid var(--border)' }} />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </Card>

      <Card title="Top event types">
        <div style={{ height: 200 }}>
          <ResponsiveContainer>
            <BarChart data={typeData} layout="vertical" margin={{ left: 10 }}>
              <XAxis type="number" hide />
              <YAxis type="category" dataKey="name" width={130} tick={{ fill: 'var(--muted)', fontSize: 11 }} />
              <RTooltip contentStyle={{ background: 'var(--bg-elev)', border: '1px solid var(--border)' }} />
              <Bar dataKey="value" radius={[0, 4, 4, 0]}>
                {typeData.map((d, i) => <Cell key={d.name} fill={CHART[i % CHART.length]} />)}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </Card>
    </>
  );
}
