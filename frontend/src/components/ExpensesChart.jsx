import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts'

export default function ExpensesChart({ pieData, PIE_COLORS }) {
  return (
    pieData.length > 0 ? (
      <ResponsiveContainer width="100%" height={200}>
        <PieChart>
          <Pie data={pieData} innerRadius={40} outerRadius={70} paddingAngle={3} dataKey="value">
            {pieData.map((_, idx) => <Cell key={idx} fill={PIE_COLORS[idx % PIE_COLORS.length]} />)}
          </Pie>
          <Tooltip 
            formatter={(v) => `₹${Number(v).toLocaleString('en-IN')}`} 
            contentStyle={{ background: 'var(--color-surface-2)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', fontSize: 'var(--font-size-sm)' }} 
          />
        </PieChart>
      </ResponsiveContainer>
    ) : (
      <div className="empty-state" style={{ padding: 'var(--space-8)' }}>
        <p className="text-muted text-sm">No data</p>
      </div>
    )
  )
}
