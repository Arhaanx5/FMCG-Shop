import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts'

export default function ExpenseBreakdownChart({ expenseChartData, activePieColors }) {
  return (
    expenseChartData.length > 0 ? (
      <ResponsiveContainer width="100%" height={250}>
        <PieChart>
          <Pie
            data={expenseChartData}
            innerRadius={60}
            outerRadius={90}
            paddingAngle={4}
            dataKey="value"
          >
            {expenseChartData.map((_, idx) => (
              <Cell key={idx} fill={activePieColors[idx % activePieColors.length]} />
            ))}
          </Pie>
          <Tooltip
            formatter={(value) => `₹${Number(value).toLocaleString('en-IN')}`}
            contentStyle={{
              background: 'var(--color-surface-2)', border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-md)', fontSize: 'var(--font-size-sm)',
            }}
          />
        </PieChart>
      </ResponsiveContainer>
    ) : (
      <div className="empty-state" style={{ padding: 'var(--space-10)' }}>
        <p className="text-muted">No expense data for this period</p>
      </div>
    )
  )
}
