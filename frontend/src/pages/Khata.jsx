import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Plus, IndianRupee, Edit2, Trash2 } from 'lucide-react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import SearchSelect from '../components/SearchSelect'
import StatCard from '../components/StatCard'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'

const emptyForm = { customerId: '', billId: '', amount: '', paymentMode: 'CASH', notes: '' }

export default function Khata() {
  const { isAdmin, isManager } = useAuth()
  const [payments, setPayments] = useState([])
  const [todayPayments, setTodayPayments] = useState([])
  const [customers, setCustomers] = useState([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [form, setForm] = useState({ ...emptyForm })
  const [saving, setSaving] = useState(false)
  const [activeTab, setActiveTab] = useState('today')
  const toast = useToast()

  // Edit Payment State
  const [editingPayment, setEditingPayment] = useState(null)
  const [editNotes, setEditNotes] = useState('')
  const [editPaymentMode, setEditPaymentMode] = useState('CASH')
  const [updating, setUpdating] = useState(false)

  useEffect(() => { loadAll() }, [])

  const loadAll = async () => {
    setLoading(true)
    try {
      const [pRes, tRes, cRes] = await Promise.all([
        api.get('/payments'),
        api.get('/payments/today'),
        api.get('/customers?size=500'),
      ])
      setPayments(pRes.data || [])
      setTodayPayments(tRes.data || [])
      setCustomers(cRes.data.data?.content || cRes.data.data || [])
    } catch { toast.error('Failed to load payments') }
    finally { setLoading(false) }
  }

  const handleSave = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const payload = { ...form, amount: Number(form.amount || 0), billId: form.billId || null }
      await api.post('/payments', payload)
      toast.success('Payment recorded!')
      setShowModal(false)
      setForm({ ...emptyForm })
      loadAll()
    } catch (err) { toast.error(err.response?.data?.message || 'Failed to record payment') }
    finally { setSaving(false) }
  }

  const deletePayment = async (id) => {
    if (!window.confirm('Are you sure you want to permanently delete this payment? Customer balance and linked bill will be reversed!')) return
    try {
      await api.delete(`/payments/${id}`)
      toast.success('Payment deleted and balance reversed successfully')
      loadAll()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Delete failed')
    }
  }

  const openEditPayment = (p) => {
    setEditingPayment(p)
    setEditNotes(p.notes || '')
    setEditPaymentMode(p.paymentMode || 'CASH')
  }

  const handleUpdatePaymentSubmit = async (e) => {
    e.preventDefault()
    setUpdating(true)
    try {
      await api.put(`/payments/${editingPayment.id}`, {
        paymentMode: editPaymentMode,
        notes: editNotes
      })
      toast.success('Payment details updated successfully')
      setEditingPayment(null)
      loadAll()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Update failed')
    } finally { setUpdating(false) }
  }

  const todayTotal = todayPayments.reduce((sum, p) => sum + Number(p.amount || 0), 0)
  const displayList = activeTab === 'today' ? todayPayments : payments
  const updateField = (key, val) => setForm(f => ({ ...f, [key]: val }))

  const columns = [
    { header: 'Customer', accessor: 'customerName', render: (row) => <span className="font-medium">{row.customerName || '—'}</span> },
    { header: 'Amount Paid', accessor: 'amount', render: (row) => <span className="font-semibold text-success">₹{Number(row.amount || 0).toLocaleString('en-IN')}</span> },
    { header: 'Pending Bal ₹', accessor: 'customerPendingBalance', render: (row) => <span className="font-semibold text-danger">₹{Number(row.customerPendingBalance || 0).toLocaleString('en-IN')}</span> },
    { header: 'Mode', accessor: 'paymentMode', render: (row) => {
      const colors = { CASH: 'badge-success', UPI: 'badge-info' }
      return <span className={`badge ${colors[row.paymentMode] || 'badge-neutral'}`}>{row.paymentMode}</span>
    }},
    { header: 'Bill #', accessor: 'billNumber', render: (row) => row.billNumber || <span className="text-muted">General</span> },
    { header: 'Notes', accessor: 'notes', render: (row) => row.notes || <span className="text-muted">—</span> },
    { header: 'Date', accessor: 'paidAt', render: (row) => row.paidAt ? new Date(row.paidAt).toLocaleDateString('en-IN') : '—' },
  ]

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">Khata / Payments</h2>
          <p className="page-subtitle">Track payment collections</p>
        </div>
        <div className="page-actions">
          <motion.button className="btn btn-primary" onClick={() => setShowModal(true)} whileTap={{ scale: 0.95 }}>
            <Plus size={18} /> Record Payment
          </motion.button>
        </div>
      </div>

      {/* KPI */}
      <div className="grid-3" style={{ marginBottom: 'var(--space-6)' }}>
        <StatCard icon={<IndianRupee size={24} />} label="Today's Collection" value={todayTotal} prefix="₹" color="var(--color-success)" delay={0} />
        <StatCard icon={<IndianRupee size={24} />} label="Today's Entries" value={todayPayments.length} color="var(--color-info)" delay={1} />
        <StatCard icon={<IndianRupee size={24} />} label="Total Payments" value={payments.length} color="var(--color-accent)" delay={2} />
      </div>

      <div className="tabs">
        <button className={`tab ${activeTab === 'today' ? 'active' : ''}`} onClick={() => setActiveTab('today')}>Today ({todayPayments.length})</button>
        <button className={`tab ${activeTab === 'all' ? 'active' : ''}`} onClick={() => setActiveTab('all')}>All Payments ({payments.length})</button>
      </div>

      <DataTable
        columns={columns}
        data={displayList}
        loading={loading}
        searchPlaceholder="Search payments..."
        emptyMessage="No payments found"
        actions={(row) => (
          <>
            {(isAdmin || isManager) && (
              <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEditPayment(row)} title="Edit Payment"><Edit2 size={15} /></button>
            )}
            {isAdmin && (
              <button className="btn btn-ghost btn-icon btn-sm" onClick={() => deletePayment(row.id)} title="Delete Payment & Reverse Balance" style={{ color: 'var(--color-danger)' }}><Trash2 size={15} /></button>
            )}
          </>
        )}
      />

      {/* Record Payment Modal */}
      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title="Record Payment">
        <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div className="form-group">
            <label className="form-label">Customer *</label>
            <SearchSelect
              options={customers.filter(c => c.active !== false)}
              value={form.customerId}
              onChange={val => updateField('customerId', val)}
              labelKey="name" valueKey="id"
              placeholder="Select customer..."
              renderOption={(c) => (
                <div className="flex justify-between">
                  <span>{c.name} {c.shopName && <span className="text-muted text-xs">— {c.shopName}</span>}</span>
                  {Number(c.totalPending || 0) > 0 && <span className="text-danger text-xs">₹{Number(c.totalPending).toLocaleString('en-IN')}</span>}
                </div>
              )}
            />
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Amount ₹ *</label>
              <input className="form-input" type="number" min="1" step="0.01" value={form.amount} onChange={e => updateField('amount', e.target.value)} required placeholder="0.00" />
            </div>
            <div className="form-group">
              <label className="form-label">Payment Mode *</label>
              <select className="form-select" value={form.paymentMode} onChange={e => updateField('paymentMode', e.target.value)}>
                <option value="CASH">Cash</option>
                <option value="UPI">UPI</option>
              </select>
            </div>
          </div>
          <div className="form-group">
            <label className="form-label">Notes</label>
            <textarea className="form-textarea" value={form.notes} onChange={e => updateField('notes', e.target.value)} placeholder="Optional notes..." rows={2} />
          </div>
          <div className="form-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
            <motion.button type="submit" className="btn btn-success" disabled={saving} whileTap={{ scale: 0.95 }}>
              {saving ? 'Recording...' : 'Record Payment'}
            </motion.button>
          </div>
        </form>
      </Modal>

      {/* Edit Payment Modal */}
      <Modal isOpen={!!editingPayment} onClose={() => setEditingPayment(null)} title="Edit Payment Details">
        {editingPayment && (
          <form onSubmit={handleUpdatePaymentSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div className="form-group">
              <label className="form-label">Customer</label>
              <input className="form-input" value={editingPayment.customerName} disabled style={{ opacity: 0.7 }} />
            </div>
            <div className="form-group">
              <label className="form-label">Amount Paid</label>
              <input className="form-input" value={`₹${Number(editingPayment.amount || 0).toLocaleString('en-IN')}`} disabled style={{ opacity: 0.7 }} />
            </div>
            <div className="form-group">
              <label className="form-label">Payment Mode</label>
              <select className="form-select" value={editPaymentMode} onChange={e => setEditPaymentMode(e.target.value)}>
                <option value="CASH">Cash</option>
                <option value="UPI">UPI</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Notes</label>
              <textarea className="form-textarea" value={editNotes} onChange={e => setEditNotes(e.target.value)} placeholder="Enter details..." rows={3} />
            </div>
            <div className="form-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setEditingPayment(null)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={updating}>
                {updating ? 'Updating...' : 'Update Details'}
              </button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  )
}
