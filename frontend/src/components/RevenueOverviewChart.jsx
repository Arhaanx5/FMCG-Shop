import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts'

const customTooltip = ({ active, payload, label }) => {
  if (active && payload && payload.length) {
    return (
      <div style={{
        background: 'var(--color-surface-2)', padding: 'var(--space-3) var(--space-4)',
        borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)',
        fontSize: 'var(--font-size-sm)',
      }}>
        <p style={{ color: 'var(--color-text)', fontWeight: 'var(--font-weight-semibold)' }}>{label}</p>
        {payload.map((p, i) => (
          <p key={i} style={{ color: p.color }}>₹{Number(p.value).toLocaleString('en-IN')}</p>
        ))}
      </div>
    )
  }
  return null
}

export default function RevenueOverviewChart({ revenueExpenseData, activeColors }) {
  return (
    <ResponsiveContainer width="100%" height={280}>
      <BarChart data={revenueExpenseData}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
        <XAxis dataKey="name" tick={{ fill: 'var(--color-text-secondary)', fontSize: 12 }} />
        <YAxis tick={{ fill: 'var(--color-text-secondary)', fontSize: 12 }} />
        <Tooltip content={customTooltip} />
        <Bar dataKey="amount" radius={[6, 6, 0, 0]}>
          {revenueExpenseData.map((entry, index) => (
            <Cell
              key={index}
              fill={activeColors[index % activeColors.length]}
            />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  )
}
