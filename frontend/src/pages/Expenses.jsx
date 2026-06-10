import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Plus, Trash2, IndianRupee } from 'lucide-react'
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import ConfirmDialog from '../components/ConfirmDialog'
import StatCard from '../components/StatCard'
import { useToast } from '../context/ToastContext'

const CATEGORIES = ['STOCK_PURCHASE', 'SALARY', 'VEHICLE_MAINTENANCE', 'FUEL', 'RENT', 'ELECTRICITY', 'PACKAGING', 'OTHER']
const CATEGORY_LABELS = {
  STOCK_PURCHASE: 'Stock Purchase (Maal Khareedi)',
  SALARY: 'Salary (Vetan)',
  VEHICLE_MAINTENANCE: 'Vehicle Maintenance (Gaadi Kharch)',
  FUEL: 'Fuel (Indhan)',
  RENT: 'Rent (Kiraya)',
  ELECTRICITY: 'Electricity (Bijli)',
  PACKAGING: 'Packaging Material',
  OTHER: 'Other Expense (Baki Kharch)'
}
const PIE_COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#6b7280']

const emptyForm = { category: 'STOCK_PURCHASE', amount: '', description: '', expenseDate: new Date().toISOString().split('T')[0], recipientId: '' }

export default function Expenses() {
  const [expenses, setExpenses] = useState([])
  const [summary, setSummary] = useState({})
  const [loading, setLoading] = useState(true)
  const [users, setUsers] = useState([])
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear())
  const [selectedMonth, setSelectedMonth] = useState(new Date().getMonth() + 1)
  const [showModal, setShowModal] = useState(false)
  const [form, setForm] = useState({ ...emptyForm })
  const [saving, setSaving] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const toast = useToast()

  useEffect(() => { loadExpenses() }, [selectedYear, selectedMonth])

  useEffect(() => {
    // Load users list for salary tracking
    api.get('/users')
      .then(res => setUsers(res.data?.data || []))
      .catch(err => console.error('Failed to load users', err))
  }, [])

  const loadExpenses = async () => {
    setLoading(true)
    try {
      const [eRes, sRes] = await Promise.all([
        api.get(`/expenses/month?year=${selectedYear}&month=${selectedMonth}`),
        api.get(`/expenses/summary?year=${selectedYear}&month=${selectedMonth}`),
      ])
      setExpenses(eRes.data || [])
      setSummary(sRes.data || {})
    } catch { toast.error('Failed to load expenses') }
    finally { setLoading(false) }
  }

  const handleSave = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const payload = {
        ...form,
        amount: Number(form.amount || 0),
        recipientId: form.category === 'SALARY' && form.recipientId ? form.recipientId : null
      }
      await api.post('/expenses', payload)
      toast.success('Expense added!')
      setShowModal(false)
      setForm({ ...emptyForm })
      loadExpenses()
    } catch (err) { toast.error(err.response?.data?.message || 'Failed to add expense') }
    finally { setSaving(false) }
  }

  const handleDelete = async () => {
    try {
      await api.delete(`/expenses/${deleteTarget}`)
      toast.success('Expense deleted')
      setDeleteTarget(null)
      loadExpenses()
    } catch { toast.error('Delete failed') }
  }

  const totalExpenses = Object.values(summary).reduce((sum, v) => sum + Number(v || 0), 0)
  
  const pieData = Object.entries(summary)
    .filter(([, v]) => Number(v) > 0)
    .map(([name, value]) => ({
      name: CATEGORY_LABELS[name] || name,
      value: Number(value)
    }))

  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  
  const updateField = (key, val) => {
    setForm(f => {
      const updated = { ...f, [key]: val }
      if (key === 'category' && val !== 'SALARY') {
        updated.recipientId = ''
      }
      return updated
    })
  }

  const columns = [
    { header: 'Category', accessor: 'category', render: (row) => {
      return <span className="badge badge-accent">{CATEGORY_LABELS[row.category] || row.category}</span>
    }},
    { header: 'Amount', accessor: 'amount', render: (row) => <span className="font-semibold">₹{Number(row.amount || 0).toLocaleString('en-IN')}</span> },
    { header: 'Recipient (For Salary)', accessor: 'recipientName', render: (row) => row.recipientName ? <span className="badge badge-info">{row.recipientName}</span> : <span className="text-muted">—</span> },
    { header: 'Description', accessor: 'description', render: (row) => row.description || <span className="text-muted">—</span> },
    { header: 'Date', accessor: 'expenseDate', render: (row) => row.expenseDate ? new Date(row.expenseDate).toLocaleDateString('en-IN') : '—' },
    { header: 'Recorded By', accessor: 'createdBy', render: (row) => row.createdBy || <span className="text-muted">—</span> },
  ]

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">Expenses</h2>
          <p className="page-subtitle">{months[selectedMonth - 1]} {selectedYear}</p>
        </div>
        <div className="page-actions">
          <select className="form-select" value={selectedMonth} onChange={e => setSelectedMonth(Number(e.target.value))} style={{ width: 120 }}>
            {months.map((m, i) => <option key={i} value={i + 1}>{m}</option>)}
          </select>
          <select className="form-select" value={selectedYear} onChange={e => setSelectedYear(Number(e.target.value))} style={{ width: 100 }}>
            {[2024, 2025, 2026].map(y => <option key={y} value={y}>{y}</option>)}
          </select>
          <motion.button className="btn btn-primary" onClick={() => setShowModal(true)} whileTap={{ scale: 0.95 }}>
            <Plus size={18} /> Add Expense
          </motion.button>
        </div>
      </div>

      {/* KPI + Pie */}
      <div className="expenses-layout" style={{ display: 'grid', gridTemplateColumns: '1fr 300px', gap: 'var(--space-6)', marginBottom: 'var(--space-6)' }}>
        <div className="grid-3">
          <StatCard icon={<IndianRupee size={24} />} label="Total Expenses" value={totalExpenses} prefix="₹" color="var(--color-danger)" delay={0} />
          <StatCard icon={<IndianRupee size={24} />} label="Entries" value={expenses.length} color="var(--color-info)" delay={1} />
          <StatCard icon={<IndianRupee size={24} />} label="Categories" value={pieData.length} color="var(--color-accent)" delay={2} />
        </div>
        <motion.div className="card" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}>
          {pieData.length > 0 ? (
            <ResponsiveContainer width="100%" height={200}>
              <PieChart>
                <Pie data={pieData} innerRadius={40} outerRadius={70} paddingAngle={3} dataKey="value">
                  {pieData.map((_, idx) => <Cell key={idx} fill={PIE_COLORS[idx % PIE_COLORS.length]} />)}
                </Pie>
                <Tooltip formatter={(v) => `₹${Number(v).toLocaleString('en-IN')}`} contentStyle={{ background: 'var(--color-surface-2)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', fontSize: 'var(--font-size-sm)' }} />
              </PieChart>
            </ResponsiveContainer>
          ) : <div className="empty-state" style={{ padding: 'var(--space-8)' }}><p className="text-muted text-sm">No data</p></div>}
        </motion.div>
      </div>

      <DataTable columns={columns} data={expenses} loading={loading} searchPlaceholder="Search expenses..." emptyMessage="No expenses this month"
        actions={(row) => <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setDeleteTarget(row.id)} style={{ color: 'var(--color-danger)' }}><Trash2 size={15} /></button>}
      />

      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title="Add Expense">
        <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Category *</label>
              <select className="form-select" value={form.category} onChange={e => updateField('category', e.target.value)}>
                {CATEGORIES.map(c => <option key={c} value={c}>{CATEGORY_LABELS[c] || c}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Amount ₹ *</label>
              <input className="form-input" type="number" min="1" step="0.01" value={form.amount} onChange={e => updateField('amount', e.target.value)} required />
            </div>
          </div>

          {form.category === 'SALARY' && (
            <div className="form-group">
              <label className="form-label">Employee / Recipient *</label>
              <select className="form-select" value={form.recipientId} onChange={e => updateField('recipientId', e.target.value)} required>
                <option value="">-- Select Employee --</option>
                {users.map(u => (
                  <option key={u.id} value={u.id}>{u.name} ({u.role})</option>
                ))}
              </select>
            </div>
          )}

          <div className="form-group">
            <label className="form-label">Description</label>
            <textarea className="form-textarea" value={form.description} onChange={e => updateField('description', e.target.value)} placeholder="What was this expense for?" rows={2} />
          </div>
          <div className="form-group">
            <label className="form-label">Date *</label>
            <input className="form-input" type="date" value={form.expenseDate} onChange={e => updateField('expenseDate', e.target.value)} required />
          </div>
          <div className="form-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
            <motion.button type="submit" className="btn btn-primary" disabled={saving} whileTap={{ scale: 0.95 }}>
              {saving ? 'Adding...' : 'Add Expense'}
            </motion.button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog isOpen={!!deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={handleDelete} title="Delete Expense" message="Are you sure you want to delete this expense?" />
    </div>
  )
}
