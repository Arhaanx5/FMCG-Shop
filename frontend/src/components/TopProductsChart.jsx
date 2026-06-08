import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'

export default function TopProductsChart({ topProductsData, uiTheme }) {
  return (
    topProductsData.length > 0 ? (
      <ResponsiveContainer width="100%" height={280}>
        <BarChart data={topProductsData} layout="vertical">
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
          <XAxis type="number" tick={{ fill: 'var(--color-text-secondary)', fontSize: 12 }} />
          <YAxis dataKey="name" type="category" tick={{ fill: 'var(--color-text-secondary)', fontSize: 11 }} width={120} />
          <Tooltip
            contentStyle={{
              background: 'var(--color-surface-2)', border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-md)', fontSize: 'var(--font-size-sm)',
            }}
          />
          <Bar
            dataKey="qty"
            fill={
              uiTheme === 'modern' ? '#8b5cf6' :
              uiTheme === 'cyber' ? '#00d2ff' :
              uiTheme === 'neon' ? '#00ffcc' :
              '#f59e0b'
            }
            radius={[0, 6, 6, 0]}
          />
        </BarChart>
      </ResponsiveContainer>
    ) : (
      <div className="empty-state" style={{ padding: 'var(--space-10)' }}>
        <p className="text-muted">No product data for this period</p>
      </div>
    )
  )
}
